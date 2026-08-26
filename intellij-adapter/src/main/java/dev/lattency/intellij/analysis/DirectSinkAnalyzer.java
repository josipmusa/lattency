package dev.lattency.intellij.analysis;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import dev.lattency.core.BuiltInSinks;
import dev.lattency.core.IoCategory;
import dev.lattency.core.LattencyConfig;
import dev.lattency.core.LattencyConfigLoader;
import dev.lattency.core.SinkFacts;
import dev.lattency.core.SinkMatcher;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Translates Java PSI into the facts understood by the pure-Java sink matcher. */
public final class DirectSinkAnalyzer {
    private static final Logger LOG = Logger.getInstance(DirectSinkAnalyzer.class);

    public List<SinkOccurrence> analyze(PsiMethod method) {
        SinkMatcher matcher = new SinkMatcher(loadConfig(method.getProject()));
        PsiClass sourceClass = method.getContainingClass();
        if (sourceClass != null
                && sourceClass.getQualifiedName() != null
                && matcher.isExcluded(sourceClass.getQualifiedName())) {
            return List.of();
        }
        if (hasAnnotation(method, BuiltInSinks.NON_BLOCKING)) {
            return List.of();
        }

        List<SinkOccurrence> occurrences = new ArrayList<>();
        matchMethod(method, matcher).ifPresent(category ->
                occurrences.add(new SinkOccurrence(method.getName() + "()", category)));
        if (method.getBody() == null) {
            return occurrences;
        }

        for (PsiMethodCallExpression call :
                PsiTreeUtil.findChildrenOfType(method.getBody(), PsiMethodCallExpression.class)) {
            PsiMethod resolvedMethod = call.resolveMethod();
            if (resolvedMethod == null) {
                continue;
            }
            matchMethod(resolvedMethod, matcher).ifPresent(category ->
                    occurrences.add(new SinkOccurrence(displayName(resolvedMethod), category)));
        }
        return List.copyOf(occurrences);
    }

    private static java.util.Optional<IoCategory> matchMethod(
            PsiMethod method, SinkMatcher matcher) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || containingClass.getQualifiedName() == null) {
            return java.util.Optional.empty();
        }
        Set<String> annotations = annotationNames(method.getAnnotations());
        annotations.addAll(annotationNames(containingClass.getAnnotations()));
        return matcher.match(new SinkFacts(
                containingClass.getQualifiedName(),
                supertypeNames(containingClass),
                method.getName(),
                annotations));
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

    private static String displayName(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass == null ? null : containingClass.getName();
        return (className == null ? "<unknown>" : className) + "." + method.getName();
    }

    private static LattencyConfig loadConfig(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return LattencyConfig.defaultsOnly();
        }
        return LattencyConfigLoader.load(
                Path.of(basePath, "lattency.yml"), message -> LOG.warn(message));
    }
}
