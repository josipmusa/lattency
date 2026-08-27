package dev.lattency.core;

import java.util.Objects;
import java.util.Set;

/**
 * IntelliJ-independent facts extracted from one resolved call or instantiation.
 *
 * <p>{@link Target} matters because type-shaped rules describe an API surface, not a
 * type's construction: {@code new ProducerRecord<>(..)} should not count as messaging
 * I/O just because the Kafka producer package is a sink. Construction is matched only
 * by {@link SinkDefinition.Kind#CONSTRUCTION} rules, which name the types whose
 * constructor really does open a resource.
 */
public record SinkFacts(
        String containingClassFqn,
        Set<String> supertypeFqns,
        String methodName,
        Set<String> annotationFqns,
        Target target) {

    public SinkFacts {
        containingClassFqn = Objects.requireNonNull(containingClassFqn, "containingClassFqn");
        supertypeFqns = Set.copyOf(Objects.requireNonNull(supertypeFqns, "supertypeFqns"));
        methodName = Objects.requireNonNull(methodName, "methodName");
        annotationFqns = Set.copyOf(Objects.requireNonNull(annotationFqns, "annotationFqns"));
        target = Objects.requireNonNull(target, "target");
    }

    public static SinkFacts ofCall(
            String containingClassFqn,
            Set<String> supertypeFqns,
            String methodName,
            Set<String> annotationFqns) {
        return new SinkFacts(
                containingClassFqn, supertypeFqns, methodName, annotationFqns, Target.CALL);
    }

    public static SinkFacts ofConstruction(
            String classFqn, Set<String> supertypeFqns, Set<String> annotationFqns) {
        return new SinkFacts(
                classFqn,
                supertypeFqns,
                classFqn.substring(classFqn.lastIndexOf('.') + 1),
                annotationFqns,
                Target.CONSTRUCTION);
    }

    public enum Target {
        CALL,
        CONSTRUCTION
    }
}
