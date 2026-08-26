package dev.lattency.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IntelliJ-independent result of classifying a method's I/O behavior: uncolored, or
 * colored with one shortest sink chain per category.
 */
public record MethodColoring(List<SinkChain> chains) {
    private static final MethodColoring UNCOLORED = new MethodColoring(List.of());

    public MethodColoring {
        chains = List.copyOf(Objects.requireNonNull(chains, "chains"));
    }

    public static MethodColoring uncolored() {
        return UNCOLORED;
    }

    /** Normalizes raw chains down to the shortest chain per category. */
    public static MethodColoring of(List<SinkChain> chains) {
        Map<IoCategory, SinkChain> shortestPerCategory = new EnumMap<>(IoCategory.class);
        for (SinkChain chain : chains) {
            shortestPerCategory.merge(chain.category(), chain,
                    (existing, candidate) ->
                            candidate.depth() < existing.depth() ? candidate : existing);
        }
        return shortestPerCategory.isEmpty()
                ? UNCOLORED
                : new MethodColoring(List.copyOf(shortestPerCategory.values()));
    }

    public boolean isColored() {
        return !chains.isEmpty();
    }

    public Set<IoCategory> categories() {
        return chains.stream().map(SinkChain::category).collect(Collectors.toUnmodifiableSet());
    }

    /** Distance to the nearest sink across all chains. */
    public int depth() {
        return chains.stream()
                .mapToInt(SinkChain::depth)
                .min()
                .orElseThrow(() -> new IllegalStateException("An uncolored method has no depth"));
    }

    public Origin origin() {
        if (!isColored()) {
            throw new IllegalStateException("An uncolored method has no origin");
        }
        return depth() == 0 ? Origin.DIRECT : Origin.TRANSITIVE;
    }

    public enum Origin {
        DIRECT,
        TRANSITIVE
    }
}
