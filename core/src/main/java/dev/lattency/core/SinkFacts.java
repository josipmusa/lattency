package dev.lattency.core;

import java.util.Objects;
import java.util.Set;

/** IntelliJ-independent facts extracted from a resolved method. */
public record SinkFacts(
        String containingClassFqn,
        Set<String> supertypeFqns,
        String methodName,
        Set<String> annotationFqns) {
    public SinkFacts {
        containingClassFqn = Objects.requireNonNull(containingClassFqn, "containingClassFqn");
        supertypeFqns = Set.copyOf(Objects.requireNonNull(supertypeFqns, "supertypeFqns"));
        methodName = Objects.requireNonNull(methodName, "methodName");
        annotationFqns = Set.copyOf(Objects.requireNonNull(annotationFqns, "annotationFqns"));
    }
}
