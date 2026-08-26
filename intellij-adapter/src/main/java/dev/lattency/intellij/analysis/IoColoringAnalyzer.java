package dev.lattency.intellij.analysis;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.lattency.core.BuiltInSinks;
import dev.lattency.core.ChainStep;
import dev.lattency.core.IoCategory;
import dev.lattency.core.MethodColoring;
import dev.lattency.core.SinkChain;
import dev.lattency.core.SinkFacts;
import dev.lattency.core.SinkMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Computes the transitive I/O coloring of Java methods.
 *
 * <p>Per-method results are cached with {@link CachedValuesManager}, invalidated on any
 * physical PSI change and on lattency.yml changes. Each cached result comes from a
 * self-contained walk that is depth-limited by the configured budget and cycle-safe
 * through a per-walk visited set. Walks reuse already-cached callee results by peeking
 * ({@link CachedValue#getUpToDateOrNull()}) — never by computing a nested cached value,
 * which would trip the platform's recursion prevention on cyclic code.
 */
public final class IoColoringAnalyzer {
    private static final Key<CachedValue<MethodColoring>> COLORING =
            Key.create("lattency.method.coloring");

    private IoColoringAnalyzer() {}

    /** Cached I/O coloring of a method declaration. */
    public static MethodColoring coloringOf(PsiMethod method) {
        CachedValue<MethodColoring> cached = method.getUserData(COLORING);
        if (cached == null) {
            cached = CachedValuesManager.getManager(method.getProject()).createCachedValue(
                    () -> {
                        LattencyConfigService service =
                                LattencyConfigService.getInstance(method.getProject());
                        Set<PsiMethod> visited = new HashSet<>();
                        visited.add(method);
                        return CachedValueProvider.Result.create(
                                walk(method, service.matcher(),
                                        service.config().depth(), visited),
                                PsiModificationTracker.MODIFICATION_COUNT,
                                service.tracker());
                    }, false);
            cached = ((UserDataHolderEx) method).putUserDataIfAbsent(COLORING, cached);
        }
        return cached.getValue();
    }

    /** Chains contributed by one call expression, as seen from the calling method. */
    public static List<SinkChain> chainsForCall(PsiMethodCallExpression call) {
        PsiMethod callee = call.resolveMethod();
        if (callee == null) {
            return List.of();
        }
        LattencyConfigService service = LattencyConfigService.getInstance(call.getProject());
        return chainsForCallee(
                callee, service.matcher(), service.config().depth(), new HashSet<>());
    }

    private static MethodColoring walk(
            PsiMethod method, SinkMatcher matcher, int budget, Set<PsiMethod> visited) {
        ProgressManager.checkCanceled();
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null
                || containingClass.getQualifiedName() == null
                || matcher.isExcluded(containingClass.getQualifiedName())
                || hasAnnotation(method, BuiltInSinks.NON_BLOCKING)) {
            return MethodColoring.uncolored();
        }

        List<SinkChain> chains = new ArrayList<>();
        String classFqn = containingClass.getQualifiedName();
        matchMethod(method, matcher).ifPresent(category -> chains.add(new SinkChain(
                category, List.of(new ChainStep(classFqn, method.getName(), false)))));

        if (method.getBody() != null) {
            for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(
                    method.getBody(), PsiMethodCallExpression.class)) {
                ProgressManager.checkCanceled();
                PsiMethod callee = call.resolveMethod();
                if (callee != null) {
                    chains.addAll(chainsForCallee(callee, matcher, budget, visited));
                }
            }
        }
        return MethodColoring.of(chains);
    }

    private static List<SinkChain> chainsForCallee(
            PsiMethod callee, SinkMatcher matcher, int budget, Set<PsiMethod> visited) {
        if (hasAnnotation(callee, BuiltInSinks.NON_BLOCKING)) {
            return List.of();
        }
        List<SinkChain> chains = new ArrayList<>();
        if (terminalEdge(callee, matcher, chains)) {
            return chains;
        }
        if (!callee.hasModifierProperty(PsiModifier.ABSTRACT)) {
            chains.addAll(walkInto(callee, matcher, budget, visited));
            return chains;
        }
        // A call through an interface or abstract method may land in any project
        // implementation: color if ANY implementation is colored (conservative-OR).
        for (PsiMethod implementation : OverridingMethodsSearch
                .search(callee, GlobalSearchScope.projectScope(callee.getProject()), true)
                .findAll()) {
            ProgressManager.checkCanceled();
            if (hasAnnotation(implementation, BuiltInSinks.NON_BLOCKING)
                    || terminalEdge(implementation, matcher, chains)) {
                continue;
            }
            chains.addAll(walkInto(implementation, matcher, budget, visited));
        }
        return chains;
    }

    /**
     * Handles edges that never recurse: sinks (one-step chain) and callees behind a
     * caching annotation (conditional one-step chain — the body may be skipped on a
     * cache hit, so the walk stops and the underlying category stays unknown).
     * Returns true when the edge was terminal.
     */
    private static boolean terminalEdge(
            PsiMethod target, SinkMatcher matcher, List<SinkChain> chains) {
        PsiClass targetClass = target.getContainingClass();
        if (targetClass == null || targetClass.getQualifiedName() == null) {
            return true;
        }
        String classFqn = targetClass.getQualifiedName();
        Optional<IoCategory> sink = matchMethod(target, matcher);
        if (sink.isPresent()) {
            chains.add(new SinkChain(
                    sink.get(), List.of(new ChainStep(classFqn, target.getName(), false))));
            return true;
        }
        if (hasCachingAnnotation(target, targetClass)) {
            chains.add(new SinkChain(IoCategory.GENERIC,
                    List.of(new ChainStep(classFqn, target.getName(), true))));
            return true;
        }
        return false;
    }

    /** Chains through a project method: cached result if available, else a sub-walk. */
    private static List<SinkChain> walkInto(
            PsiMethod target, SinkMatcher matcher, int budget, Set<PsiMethod> visited) {
        PsiClass targetClass = target.getContainingClass();
        if (targetClass == null || targetClass.getQualifiedName() == null
                || matcher.isExcluded(targetClass.getQualifiedName())
                || !target.getManager().isInProject(target)) {
            return List.of();
        }
        MethodColoring coloring = cachedOrSubWalk(target, matcher, budget, visited);
        if (coloring == null || !coloring.isColored()) {
            return List.of();
        }
        ChainStep step =
                new ChainStep(targetClass.getQualifiedName(), target.getName(), false);
        List<SinkChain> chains = new ArrayList<>();
        for (SinkChain chain : coloring.chains()) {
            if (chain.depth() + 1 <= budget) {
                chains.add(chain.prefixedWith(step));
            }
        }
        return chains;
    }

    private static MethodColoring cachedOrSubWalk(
            PsiMethod target, SinkMatcher matcher, int budget, Set<PsiMethod> visited) {
        CachedValue<MethodColoring> cached = target.getUserData(COLORING);
        if (cached != null) {
            Getter<MethodColoring> upToDate = cached.getUpToDateOrNull();
            if (upToDate != null) {
                return upToDate.get();
            }
        }
        if (budget == 0 || !visited.add(target)) {
            return null; // Depth limit reached, or a cycle back into the current walk.
        }
        try {
            return walk(target, matcher, budget - 1, visited);
        } finally {
            visited.remove(target);
        }
    }

    private static Optional<IoCategory> matchMethod(PsiMethod method, SinkMatcher matcher) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() == null) {
            return Optional.empty();
        }
        Set<String> annotations = annotationNames(method.getAnnotations());
        annotations.addAll(annotationNames(containingClass.getAnnotations()));
        return matcher.match(new SinkFacts(
                containingClass.getQualifiedName(),
                supertypeNames(containingClass),
                method.getName(),
                annotations));
    }

    private static boolean hasCachingAnnotation(PsiMethod method, PsiClass containingClass) {
        Set<String> annotations = annotationNames(method.getAnnotations());
        annotations.addAll(annotationNames(containingClass.getAnnotations()));
        return annotations.stream().anyMatch(BuiltInSinks::isCachingAnnotation);
    }

    private static Set<String> supertypeNames(PsiClass psiClass) {
        Set<String> names = new HashSet<>();
        Set<PsiClass> visited = new HashSet<>();
        var queue = new ArrayDeque<PsiClass>();
        for (PsiClass directSuper : psiClass.getSupers()) {
            queue.add(directSuper);
        }
        while (!queue.isEmpty()) {
            PsiClass current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.getQualifiedName() != null) {
                names.add(current.getQualifiedName());
            }
            for (PsiClass parent : current.getSupers()) {
                queue.add(parent);
            }
        }
        return names;
    }

    private static Set<String> annotationNames(PsiAnnotation[] annotations) {
        Set<String> names = new HashSet<>();
        for (PsiAnnotation annotation : annotations) {
            if (annotation.getQualifiedName() != null) {
                names.add(annotation.getQualifiedName());
            }
        }
        return names;
    }

    private static boolean hasAnnotation(PsiMethod method, String annotationFqn) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            if (annotationFqn.equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
