package dev.lattency.core;

import java.util.Objects;

/**
 * One link in a sink chain: the method reached at this hop, plus whether the edge
 * into it is conditional (e.g. the callee is behind a caching annotation).
 */
public record ChainStep(String classFqn, String methodName, boolean conditional) {
    public ChainStep {
        Objects.requireNonNull(classFqn, "classFqn");
        Objects.requireNonNull(methodName, "methodName");
        if (classFqn.isBlank() || methodName.isBlank()) {
            throw new IllegalArgumentException("A chain step needs a class and a method name");
        }
    }

    /** Short human-readable form, e.g. {@code OrderRepository.save}. */
    public String display() {
        return classFqn.substring(classFqn.lastIndexOf('.') + 1) + "." + methodName;
    }
}
