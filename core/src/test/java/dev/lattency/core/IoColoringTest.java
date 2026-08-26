package dev.lattency.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class IoColoringTest {
    @Test
    void defensivelyCopiesCategories() {
        var categories = new java.util.HashSet<>(Set.of(IoCategory.DB));
        var coloring = new IoColoring(categories, IoColoring.Origin.DIRECT);

        categories.clear();

        assertEquals(Set.of(IoCategory.DB), coloring.categories());
    }

    @Test
    void rejectsEmptyCategorySet() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IoColoring(Set.of(), IoColoring.Origin.DIRECT));
    }
}
