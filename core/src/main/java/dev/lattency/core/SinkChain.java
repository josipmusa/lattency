package dev.lattency.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A path from a method to one I/O sink: the callee at each hop, ending at the sink
 * itself. A direct sink call is a single-step chain (depth 0).
 */
public record SinkChain(IoCategory category, List<ChainStep> steps) {
    public SinkChain {
        Objects.requireNonNull(category, "category");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A sink chain needs at least one step");
        }
    }

    /** Number of project-method hops before the sink; a direct sink call is depth 0. */
    public int depth() {
        return steps.size() - 1;
    }

    public boolean conditional() {
        return steps.stream().anyMatch(ChainStep::conditional);
    }

    /** The same chain as seen from a caller reaching this chain through {@code step}. */
    public SinkChain prefixedWith(ChainStep step) {
        List<ChainStep> extended = new ArrayList<>();
        extended.add(step);
        extended.addAll(steps);
        return new SinkChain(category, extended);
    }
}
