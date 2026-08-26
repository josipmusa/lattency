package dev.lattency.core;

import java.util.List;
import java.util.Objects;

/** Project configuration combined with the built-in rules by the adapter. */
public record LattencyConfig(List<SinkDefinition> sinks, List<String> exclusions) {
    public LattencyConfig {
        sinks = List.copyOf(Objects.requireNonNull(sinks, "sinks"));
        exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
    }

    public static LattencyConfig defaultsOnly() {
        return new LattencyConfig(List.of(), List.of());
    }
}
