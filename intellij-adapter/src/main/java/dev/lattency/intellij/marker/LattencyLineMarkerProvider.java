package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.search.GlobalSearchScope;
import dev.lattency.core.ChainStep;
import dev.lattency.core.MethodColoring;
import dev.lattency.core.SinkChain;
import dev.lattency.intellij.LattencyIcons;
import dev.lattency.intellij.analysis.IoColoringAnalyzer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Gutter markers for I/O-colored code: on the declaration of every colored method,
 * and on every line invoking one. All analysis happens in the slow marker pass.
 */
public final class LattencyLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private static final String DECLARATION_HEADER = "Lattency I/O:";
    private static final String CALL_SITE_HEADER = "Lattency I/O call:";

    @Override
    public void collectNavigationMarkers(
            @NotNull List<? extends PsiElement> elements,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
            boolean forNavigation) {
        if (elements.isEmpty() || DumbService.isDumb(elements.getFirst().getProject())) {
            return;
        }
        Map<CallLine, List<SinkChain>> chainsPerLine = new LinkedHashMap<>();
        Map<CallLine, PsiElement> anchorPerLine = new LinkedHashMap<>();
        for (PsiElement element : elements) {
            ProgressManager.checkCanceled();
            if (element instanceof PsiIdentifier identifier
                    && identifier.getParent() instanceof PsiMethod method) {
                collectDeclarationMarker(identifier, method, result);
            } else if (element instanceof PsiMethodCallExpression call) {
                collectCallSite(call, chainsPerLine, anchorPerLine);
            }
        }
        for (Map.Entry<CallLine, List<SinkChain>> entry : chainsPerLine.entrySet()) {
            result.add(callSiteMarker(anchorPerLine.get(entry.getKey()), entry.getValue()));
        }
    }

    private static void collectDeclarationMarker(
            PsiIdentifier identifier,
            PsiMethod method,
            Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        MethodColoring coloring = IoColoringAnalyzer.coloringOf(method);
        if (!coloring.isColored()) {
            return;
        }
        Icon icon = LattencyIcons.forColoring(coloring.categories(), coloring.origin());
        result.add(NavigationGutterIconBuilder.create(icon)
                .setTargets(chainTargets(method.getProject(), coloring.chains()))
                .setPopupTitle("Lattency sink chain")
                .setTooltipText(declarationTooltip(method.getName(), coloring.chains()))
                .createLineMarkerInfo(identifier));
    }

    private static void collectCallSite(
            PsiMethodCallExpression call,
            Map<CallLine, List<SinkChain>> chainsPerLine,
            Map<CallLine, PsiElement> anchorPerLine) {
        PsiElement anchor = call.getMethodExpression().getReferenceNameElement();
        if (anchor == null) {
            return;
        }
        CallLine line = CallLine.of(anchor);
        if (line == null) {
            return;
        }
        List<SinkChain> chains = IoColoringAnalyzer.chainsForCall(call);
        if (chains.isEmpty()) {
            return;
        }
        chainsPerLine.computeIfAbsent(line, ignored -> new ArrayList<>()).addAll(chains);
        anchorPerLine.merge(line, anchor, (existing, candidate) ->
                existing.getTextOffset() <= candidate.getTextOffset() ? existing : candidate);
    }

    private static RelatedItemLineMarkerInfo<PsiElement> callSiteMarker(
            PsiElement anchor, List<SinkChain> chains) {
        Project project = anchor.getProject();
        MethodColoring lineColoring = MethodColoring.of(chains);
        Icon icon = LattencyIcons.forColoring(lineColoring.categories(), lineColoring.origin());
        return NavigationGutterIconBuilder.create(icon)
                .setTargets(chainTargets(project, chains))
                .setPopupTitle("Lattency sink chain")
                .setTooltipText(tooltip(CALL_SITE_HEADER, chains, chain -> ""))
                .createLineMarkerInfo(anchor);
    }

    private static String declarationTooltip(String methodName, List<SinkChain> chains) {
        return tooltip(DECLARATION_HEADER, chains, chain -> methodName + " &rarr; ");
    }

    private static String tooltip(
            String header,
            List<SinkChain> chains,
            java.util.function.Function<SinkChain, String> prefix) {
        return "<html>" + header + "<br/>"
                + chains.stream()
                        .map(chain -> prefix.apply(chain) + renderChain(chain))
                        .collect(Collectors.joining("<br/>"))
                + "</html>";
    }

    private static String renderChain(SinkChain chain) {
        return chain.steps().stream()
                .map(step -> step.conditional()
                        ? step.display() + " (@Cacheable: conditional)"
                        : step.display())
                .collect(Collectors.joining(" &rarr; "))
                + " &rarr; [" + chain.category() + "]";
    }

    /** Every method along the chains, resolved fresh so the cache never holds PSI. */
    private static List<PsiMethod> chainTargets(Project project, List<SinkChain> chains) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        LinkedHashSet<PsiMethod> targets = new LinkedHashSet<>();
        for (SinkChain chain : chains) {
            for (ChainStep step : chain.steps()) {
                var psiClass = facade.findClass(step.classFqn(), scope);
                if (psiClass != null) {
                    targets.addAll(Arrays.asList(
                            psiClass.findMethodsByName(step.methodName(), false)));
                }
            }
        }
        return List.copyOf(targets);
    }

    private record CallLine(PsiFile file, int line) {
        static @Nullable CallLine of(PsiElement element) {
            PsiFile file = element.getContainingFile();
            if (file == null) {
                return null;
            }
            Document document =
                    PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
            if (document == null) {
                return null;
            }
            return new CallLine(file, document.getLineNumber(element.getTextOffset()));
        }
    }

    @Override
    public String getName() {
        return "Lattency I/O";
    }

    @Override
    public @Nullable Icon getIcon() {
        return LattencyIcons.GENERIC;
    }
}
