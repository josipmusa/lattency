package dev.lattency.core;

import java.util.Objects;

/** A declarative rule for recognizing one I/O sink. */
public record SinkDefinition(Kind kind, String pattern, String methodName, IoCategory category) {
    public SinkDefinition {
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
        Objects.requireNonNull(category, "category");
    }

    public static SinkDefinition packagePrefix(String prefix, IoCategory category) {
        return new SinkDefinition(Kind.PACKAGE_PREFIX, prefix, null, category);
    }

    public static SinkDefinition className(String fqn, IoCategory category) {
        return new SinkDefinition(Kind.CLASS, fqn, null, category);
    }

    public static SinkDefinition method(String classFqn, String methodName, IoCategory category) {
        return new SinkDefinition(Kind.METHOD, classFqn, methodName, category);
    }

    public static SinkDefinition annotation(String annotationFqn, IoCategory category) {
        return new SinkDefinition(Kind.ANNOTATION, annotationFqn, null, category);
    }

    public static SinkDefinition supertype(String supertypeFqn, IoCategory category) {
        return new SinkDefinition(Kind.SUPERTYPE, supertypeFqn, null, category);
    }

    public enum Kind {
        PACKAGE_PREFIX,
        CLASS,
        METHOD,
        ANNOTATION,
        SUPERTYPE
    }
}
