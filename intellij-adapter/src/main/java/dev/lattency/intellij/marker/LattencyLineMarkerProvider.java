package dev.lattency.intellij.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import dev.lattency.core.IoCategory;
import dev.lattency.intellij.LattencyIcons;
import dev.lattency.intellij.analysis.DirectSinkAnalyzer;
import dev.lattency.intellij.analysis.SinkOccurrence;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/** Adds direct-I/O markers to Java method-name identifiers. */
public final class LattencyLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private final DirectSinkAnalyzer analyzer = new DirectSinkAnalyzer();

    @Override
    protected void collectNavigationMarkers(
            @NotNull PsiElement element,
            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier identifier)
                || !(identifier.getParent() instanceof PsiMethod method)) {
            return;
        }
        if (DumbService.isDumb(element.getProject())) {
            return;
        }

        var occurrences = analyzer.analyze(method);
        if (occurrences.isEmpty()) {
            return;
        }
        Set<IoCategory> categories = occurrences.stream()
                .map(SinkOccurrence::category)
                .collect(Collectors.toUnmodifiableSet());
        var marker = NavigationGutterIconBuilder.create(LattencyIcons.forCategories(categories))
                .setTarget(method)
                .setTooltipText(tooltip(occurrences))
                .createLineMarkerInfo(identifier);
        result.add(marker);
    }

    private static String tooltip(java.util.List<SinkOccurrence> occurrences) {
        return "<html>Lattency direct I/O:<br>"
                + occurrences.stream()
                        .map(occurrence -> occurrence.callee()
                                + " &rarr; ["
                                + occurrence.category()
                                + "]")
                        .collect(Collectors.joining("<br>"))
                + "</html>";
    }
}
