package dev.lattency.intellij.analysis;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import dev.lattency.core.BuiltInSinks;
import dev.lattency.core.ChainStep;
import dev.lattency.core.IoCategory;
import dev.lattency.core.MethodColoring;
import dev.lattency.core.SinkChain;
import dev.lattency.core.SinkFacts;
import dev.lattency.core.SinkMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the transitive I/O coloring of Java methods.
 *
 * <p>Per-method results are cached with {@link CachedValuesManager}, invalidated on any
 * physical PSI change and on lattency.yml changes. Each cached result comes from a
 * self-contained {@link Walk} that is depth-limited by the configured budget and
 * cycle-safe through a per-walk path set. Walks reuse already-cached callee results by
 * peeking ({@link CachedValue#getUpToDateOrNull()}) - never by computing a nested cached
 * value, which would trip the platform's recursion prevention on cyclic code.
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
                        Walk walk = new Walk(service.matcher());
                        walk.path.add(method);
                        return CachedValueProvider.Result.create(
                                walk(method, walk, service.config().depth()),
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
        return chainsForCallee(callee, new Walk(service.matcher()), service.config().depth());
    }

    /** Chains contributed by one instantiation, as seen from the enclosing method. */
    public static List<SinkChain> chainsForConstruction(PsiNewExpression construction) {
        LattencyConfigService service =
                LattencyConfigService.getInstance(construction.getProject());
        return chainsForConstruction(
                construction, new Walk(service.matcher()), service.config().depth());
    }

    private static MethodColoring walk(PsiMethod method, Walk walk, int budget) {
        ProgressManager.checkCanceled();
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null
                || containingClass.getQualifiedName() == null
                || walk.matcher.isExcluded(containingClass.getQualifiedName())
                || hasAnnotation(method, BuiltInSinks.NON_BLOCKING)) {
            return MethodColoring.uncolored();
        }

        List<SinkChain> chains = new ArrayList<>();
        String classFqn = containingClass.getQualifiedName();
        matchTarget(method, walk.matcher).ifPresent(category -> chains.add(new SinkChain(
                category, List.of(new ChainStep(classFqn, method.getName(), false)))));

        if (method.getBody() != null) {
            method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethodCallExpression(PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    ProgressManager.checkCanceled();
                    PsiMethod callee = call.resolveMethod();
                    if (callee != null) {
                        chains.addAll(chainsForCallee(callee, walk, budget));
                    }
                }

                @Override
                public void visitNewExpression(PsiNewExpression construction) {
                    super.visitNewExpression(construction);
                    ProgressManager.checkCanceled();
                    chains.addAll(chainsForConstruction(construction, walk, budget));
                }
            });
        }
        return MethodColoring.of(chains);
    }

    private static List<SinkChain> chainsForCallee(PsiMethod callee, Walk walk, int budget) {
        if (hasAnnotation(callee, BuiltInSinks.NON_BLOCKING)) {
            return List.of();
        }
        List<SinkChain> chains = new ArrayList<>();
        if (terminalEdge(callee, walk.matcher, chains)) {
            return chains;
        }
        if (!callee.hasModifierProperty(PsiModifier.ABSTRACT)) {
            chains.addAll(walkInto(callee, walk, budget));
            return chains;
        }
        // A call through an interface or abstract method may land in any project
        // implementation: color if ANY implementation is colored (conservative-OR).
        for (PsiMethod implementation : OverridingMethodsSearch
                .search(callee, GlobalSearchScope.projectScope(callee.getProject()), true)
                .findAll()) {
            ProgressManager.checkCanceled();
            if (hasAnnotation(implementation, BuiltInSinks.NON_BLOCKING)
                    || terminalEdge(implementation, walk.matcher, chains)) {
                continue;
            }
            chains.addAll(walkInto(implementation, walk, budget));
        }
        return chains;
    }

    /**
     * Chains contributed by {@code new X(..)}: either X's construction is itself a sink,
     * or X is project code whose constructor body is walked like any other callee.
     */
    private static List<SinkChain> chainsForConstruction(
            PsiNewExpression construction, Walk walk, int budget) {
        PsiClass instantiated = resolveInstantiatedClass(construction);
        if (instantiated == null || instantiated.getQualifiedName() == null) {
            return List.of();
        }
        String classFqn = instantiated.getQualifiedName();
        PsiMethod constructor = construction.resolveConstructor();
        if (constructor != null && hasAnnotation(constructor, BuiltInSinks.NON_BLOCKING)) {
            return List.of();
        }

        Set<String> annotations = annotationNames(instantiated.getAnnotations());
        if (constructor != null) {
            annotations.addAll(annotationNames(constructor.getAnnotations()));
        }
        Optional<IoCategory> sink = walk.matcher.match(SinkFacts.ofConstruction(
                classFqn, supertypeNames(instantiated), annotations));
        if (sink.isPresent()) {
            return List.of(new SinkChain(sink.get(), List.of(new ChainStep(
                    classFqn, instantiated.getName() == null ? classFqn : instantiated.getName(),
                    false))));
        }
        return constructor == null ? List.of() : walkInto(constructor, walk, budget);
    }

    private static @Nullable PsiClass resolveInstantiatedClass(PsiNewExpression construction) {
        PsiMethod constructor = construction.resolveConstructor();
        if (constructor != null && constructor.getContainingClass() != null) {
            return constructor.getContainingClass();
        }
        PsiJavaCodeReferenceElement reference = construction.getClassReference();
        if (reference == null) {
            return null;
        }
        PsiElement resolved = reference.resolve();
        return resolved instanceof PsiClass psiClass ? psiClass : null;
    }

    /**
     * Handles edges that never recurse: sinks (one-step chain) and callees behind a
     * caching annotation (conditional one-step chain - the body may be skipped on a
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
        Optional<IoCategory> sink = matchTarget(target, matcher);
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
    private static List<SinkChain> walkInto(PsiMethod target, Walk walk, int budget) {
        PsiClass targetClass = target.getContainingClass();
        if (targetClass == null || targetClass.getQualifiedName() == null
                || walk.matcher.isExcluded(targetClass.getQualifiedName())
                || !target.getManager().isInProject(target)) {
            return List.of();
        }
        MethodColoring coloring = cachedOrSubWalk(target, walk, budget);
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

    private static @Nullable MethodColoring cachedOrSubWalk(
            PsiMethod target, Walk walk, int budget) {
        CachedValue<MethodColoring> cached = target.getUserData(COLORING);
        if (cached != null) {
            Getter<MethodColoring> upToDate = cached.getUpToDateOrNull();
            if (upToDate != null) {
                return upToDate.get();
            }
        }
        MethodColoring memoized = walk.recall(target, budget);
        if (memoized != null) {
            return memoized;
        }
        if (budget == 0) {
            return null; // Depth limit reached.
        }
        if (!walk.path.add(target)) {
            walk.cycleCuts++;
            return null; // A cycle back into the current walk.
        }
        int cutsBeforeSubtree = walk.cycleCuts;
        try {
            MethodColoring result = walk(target, walk, budget - 1);
            if (walk.cycleCuts == cutsBeforeSubtree) {
                walk.remember(target, budget, result);
            }
            return result;
        } finally {
            walk.path.remove(target);
        }
    }

    /**
     * Matches a method against the sink rules as its own declaration. Constructors are
     * matched as construction, not as calls: {@code java.io.File} is a sink because its
     * methods touch the filesystem, but {@code new File(name)} touches nothing.
     */
    private static Optional<IoCategory> matchTarget(PsiMethod method, SinkMatcher matcher) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() == null) {
            return Optional.empty();
        }
        Set<String> annotations = annotationNames(method.getAnnotations());
        annotations.addAll(annotationNames(containingClass.getAnnotations()));
        String classFqn = containingClass.getQualifiedName();
        Set<String> supertypes = supertypeNames(containingClass);
        return matcher.match(method.isConstructor()
                ? SinkFacts.ofConstruction(classFqn, supertypes, annotations)
                : SinkFacts.ofCall(classFqn, supertypes, method.getName(), annotations));
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

    /**
     * State of one depth-limited walk.
     *
     * <p>{@code path} makes the walk cycle-safe; {@code memo} makes it cheap. Without the
     * memo, a call graph that fans out and re-converges (the common shape: several methods
     * of a service delegating to the same helpers) re-derives every shared subtree once per
     * path reaching it, which is exponential in the depth limit.
     *
     * <p>A memoized result is reused for any budget at or below the one it was computed
     * with: {@link MethodColoring} keeps only the shortest chain per category, so a chain
     * missing from a deeper walk cannot exist in a shallower one, and {@link #walkInto}
     * drops the chains that are now too deep. Results are memoized only when no cycle was
     * cut while computing them - a cut result depends on which methods were on the path,
     * so it is not reusable elsewhere in the walk.
     */
    private static final class Walk {
        private final SinkMatcher matcher;
        private final Set<PsiMethod> path = new HashSet<>();
        private final Map<PsiMethod, Memo> memo = new HashMap<>();
        private int cycleCuts;

        private Walk(SinkMatcher matcher) {
            this.matcher = matcher;
        }

        private @Nullable MethodColoring recall(PsiMethod method, int budget) {
            Memo remembered = memo.get(method);
            return remembered != null && remembered.budget >= budget ? remembered.coloring : null;
        }

        private void remember(PsiMethod method, int budget, MethodColoring coloring) {
            Memo remembered = memo.get(method);
            if (remembered == null || remembered.budget < budget) {
                memo.put(method, new Memo(budget, coloring));
            }
        }

        private record Memo(int budget, MethodColoring coloring) {}
    }
}
