package dev.lattency.core;

import java.util.Objects;

/**
 * The shape of one sink rule: which calls or instantiations it applies to. Shared by
 * {@link SinkDefinition} (this shape is I/O of some category) and by the
 * {@code ignore.sinks} entries of {@code lattency.yml} (this shape is never I/O).
 */
public record SinkPattern(Kind kind, String pattern, String methodName) {
    public SinkPattern {
        Objects.requireNonNull(kind, "kind");
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Sink pattern must not be blank");
        }
        if (kind == Kind.METHOD && (methodName == null || methodName.isBlank())) {
            throw new IllegalArgumentException("A method sink needs a method name");
        }
        if (kind != Kind.METHOD && methodName != null) {
            throw new IllegalArgumentException("Only method sinks may have a method name");
        }
    }

    public static SinkPattern packagePrefix(String prefix) {
        return new SinkPattern(Kind.PACKAGE_PREFIX, prefix, null);
    }

    public static SinkPattern className(String fqn) {
        return new SinkPattern(Kind.CLASS, fqn, null);
    }

    public static SinkPattern method(String classFqn, String methodName) {
        return new SinkPattern(Kind.METHOD, classFqn, methodName);
    }

    public static SinkPattern annotation(String annotationFqn) {
        return new SinkPattern(Kind.ANNOTATION, annotationFqn, null);
    }

    public static SinkPattern supertype(String supertypeFqn) {
        return new SinkPattern(Kind.SUPERTYPE, supertypeFqn, null);
    }

    public static SinkPattern construction(String classFqn) {
        return new SinkPattern(Kind.CONSTRUCTION, classFqn, null);
    }

    public boolean matches(SinkFacts facts) {
        boolean construction = facts.target() == SinkFacts.Target.CONSTRUCTION;
        return switch (kind) {
            // Type-shaped rules describe an API surface; constructing the type is not
            // calling it (see SinkFacts).
            case PACKAGE_PREFIX -> !construction
                    && (facts.containingClassFqn().equals(pattern)
                            || facts.containingClassFqn().startsWith(pattern + "."));
            case CLASS -> !construction && facts.containingClassFqn().equals(pattern);
            case METHOD -> !construction
                    && facts.containingClassFqn().equals(pattern)
                    && facts.methodName().equals(methodName);
            case SUPERTYPE -> !construction && facts.supertypeFqns().contains(pattern);
            // Annotations name the target itself, so they apply to constructors too.
            case ANNOTATION -> facts.annotationFqns().contains(pattern);
            case CONSTRUCTION -> construction && facts.containingClassFqn().equals(pattern);
        };
    }

    public enum Kind {
        PACKAGE_PREFIX,
        CLASS,
        METHOD,
        ANNOTATION,
        SUPERTYPE,
        CONSTRUCTION
    }
}
