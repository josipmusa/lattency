package dev.lattency.core;

import java.util.List;
import java.util.Objects;

/** Project configuration combined with the built-in rules by the adapter. */
public record LattencyConfig(List<SinkDefinition> sinks, List<String> exclusions, int depth) {
    public static final int DEFAULT_DEPTH = 4;
    public static final int MAX_DEPTH = 10;

    public LattencyConfig {
        sinks = List.copyOf(Objects.requireNonNull(sinks, "sinks"));
        exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
        if (depth < 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "depth must be between 0 and " + MAX_DEPTH + ", got " + depth);
        }
    }

    public static LattencyConfig defaultsOnly() {
        return new LattencyConfig(List.of(), List.of(), DEFAULT_DEPTH);
    }
}
