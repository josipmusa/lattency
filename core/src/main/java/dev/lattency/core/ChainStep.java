package dev.lattency.core;

import java.util.Objects;

/**
 * One link in a sink chain: a method reference in display form, plus whether the
 * edge into it is conditional (e.g. the callee is behind a caching annotation).
 */
public record ChainStep(String methodReference, boolean conditional) {
    public ChainStep {
        Objects.requireNonNull(methodReference, "methodReference");
        if (methodReference.isBlank()) {
            throw new IllegalArgumentException("A chain step needs a method reference");
        }
    }
}
