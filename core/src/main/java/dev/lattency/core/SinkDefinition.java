package dev.lattency.core;

import java.util.Objects;

/** A declarative rule for recognizing one I/O sink: a {@link SinkPattern} and its category. */
public record SinkDefinition(SinkPattern shape, IoCategory category) {
    public SinkDefinition {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(category, "category");
    }

    public SinkPattern.Kind kind() {
        return shape.kind();
    }

    public String pattern() {
        return shape.pattern();
    }

    public String methodName() {
        return shape.methodName();
    }

    public static SinkDefinition packagePrefix(String prefix, IoCategory category) {
        return new SinkDefinition(SinkPattern.packagePrefix(prefix), category);
    }

    public static SinkDefinition className(String fqn, IoCategory category) {
        return new SinkDefinition(SinkPattern.className(fqn), category);
    }

    public static SinkDefinition method(String classFqn, String methodName, IoCategory category) {
        return new SinkDefinition(SinkPattern.method(classFqn, methodName), category);
    }

    public static SinkDefinition annotation(String annotationFqn, IoCategory category) {
        return new SinkDefinition(SinkPattern.annotation(annotationFqn), category);
    }

    public static SinkDefinition supertype(String supertypeFqn, IoCategory category) {
        return new SinkDefinition(SinkPattern.supertype(supertypeFqn), category);
    }

    /** Matches {@code new <fqn>(...)} - for types whose construction is itself the I/O. */
    public static SinkDefinition construction(String classFqn, IoCategory category) {
        return new SinkDefinition(SinkPattern.construction(classFqn), category);
    }
}
