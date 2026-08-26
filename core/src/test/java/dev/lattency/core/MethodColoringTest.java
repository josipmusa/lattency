package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MethodColoringTest {
    private static final ChainStep SAVE = new ChainStep("example.OrderRepository", "save", false);
    private static final ChainStep BOTTOM = new ChainStep("example.Example", "bottom", false);
    private static final ChainStep MIDDLE = new ChainStep("example.Example", "middle", false);

    @Test
    void uncoloredHasNoChainsAndNoCategories() {
        MethodColoring coloring = MethodColoring.uncolored();

        assertFalse(coloring.isColored());
        assertEquals(Set.of(), coloring.categories());
        assertEquals(List.of(), coloring.chains());
    }

    @Test
    void directChainYieldsDirectOriginAndDepthZero() {
        MethodColoring coloring = MethodColoring.of(List.of(
                new SinkChain(IoCategory.DB, List.of(SAVE))));

        assertTrue(coloring.isColored());
        assertEquals(Set.of(IoCategory.DB), coloring.categories());
        assertEquals(MethodColoring.Origin.DIRECT, coloring.origin());
        assertEquals(0, coloring.depth());
    }

    @Test
    void transitiveChainYieldsTransitiveOriginAndEdgeCountDepth() {
        MethodColoring coloring = MethodColoring.of(List.of(
                new SinkChain(IoCategory.DB, List.of(MIDDLE, BOTTOM, SAVE))));

        assertEquals(MethodColoring.Origin.TRANSITIVE, coloring.origin());
        assertEquals(2, coloring.depth());
    }

    @Test
    void anyDirectChainMakesTheColoringDirect() {
        MethodColoring coloring = MethodColoring.of(List.of(
                new SinkChain(IoCategory.HTTP, List.of(MIDDLE, new ChainStep("example.Client", "post", false))),
                new SinkChain(IoCategory.DB, List.of(SAVE))));

        assertEquals(MethodColoring.Origin.DIRECT, coloring.origin());
        assertEquals(0, coloring.depth());
    }

    @Test
    void keepsOnlyTheShortestChainPerCategory() {
        SinkChain shortChain = new SinkChain(IoCategory.DB, List.of(BOTTOM, SAVE));
        SinkChain longChain = new SinkChain(IoCategory.DB, List.of(MIDDLE, BOTTOM, SAVE));

        MethodColoring coloring = MethodColoring.of(List.of(longChain, shortChain));

        assertEquals(List.of(shortChain), coloring.chains());
    }

    @Test
    void keepsOneChainPerDistinctCategory() {
        SinkChain db = new SinkChain(IoCategory.DB, List.of(SAVE));
        SinkChain http = new SinkChain(IoCategory.HTTP, List.of(new ChainStep("example.Client", "post", false)));

        MethodColoring coloring = MethodColoring.of(List.of(db, http));

        assertEquals(Set.of(IoCategory.DB, IoCategory.HTTP), coloring.categories());
        assertEquals(2, coloring.chains().size());
    }

    @Test
    void chainConditionalWhenAnyStepIsConditional() {
        SinkChain conditional = new SinkChain(
                IoCategory.GENERIC, List.of(new ChainStep("example.OrderService", "find", true)));
        SinkChain unconditional = new SinkChain(IoCategory.DB, List.of(SAVE));

        assertTrue(conditional.conditional());
        assertFalse(unconditional.conditional());
    }

    @Test
    void chainPrefixedWithProducesTheCallerChain() {
        SinkChain calleeChain = new SinkChain(IoCategory.DB, List.of(BOTTOM, SAVE));

        SinkChain callerChain = calleeChain.prefixedWith(MIDDLE);

        assertEquals(new SinkChain(IoCategory.DB, List.of(MIDDLE, BOTTOM, SAVE)), callerChain);
    }

    @Test
    void depthAndOriginAreUndefinedForUncolored() {
        assertThrows(IllegalStateException.class, () -> MethodColoring.uncolored().depth());
        assertThrows(IllegalStateException.class, () -> MethodColoring.uncolored().origin());
    }

    @Test
    void rejectsEmptyChainSteps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SinkChain(IoCategory.DB, List.of()));
    }

    @Test
    void stepDisplayUsesSimpleClassName() {
        assertEquals("OrderRepository.save", SAVE.display());
        assertEquals("Inner.run", new ChainStep("a.b.Outer.Inner", "run", false).display());
    }
}
