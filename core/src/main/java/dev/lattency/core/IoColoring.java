package dev.lattency.core;

import java.util.Objects;
import java.util.Set;

/** IntelliJ-independent result of classifying a method's I/O behavior. */
public record IoColoring(Set<IoCategory> categories, Origin origin) {
    public IoColoring {
        categories = Set.copyOf(Objects.requireNonNull(categories, "categories"));
        Objects.requireNonNull(origin, "origin");
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("An I/O coloring needs at least one category");
        }
    }

    public enum Origin {
        DIRECT,
        TRANSITIVE,
        CONDITIONAL
    }
}

