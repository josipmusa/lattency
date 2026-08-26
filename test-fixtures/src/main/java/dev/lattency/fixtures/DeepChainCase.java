package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

/**
 * Chain longer than the default depth limit (4): level0..level4 are marked,
 * level5 and level6 are beyond the limit and stay unmarked.
 */
public final class DeepChainCase {
    private final OrderRepository repository;

    public DeepChainCase(OrderRepository repository) {
        this.repository = repository;
    }

    // Expect: no marker (depth 6 > limit 4).
    public void level6(Order order) {
        level5(order);
    }

    // Expect: no marker (depth 5 > limit 4).
    private void level5(Order order) {
        level4(order);
    }

    // Expect: DB marker, transitive (depth 4 = limit).
    private void level4(Order order) {
        level3(order);
    }

    // Expect: DB marker, transitive (depth 3).
    private void level3(Order order) {
        level2(order);
    }

    // Expect: DB marker, transitive (depth 2).
    private void level2(Order order) {
        level1(order);
    }

    // Expect: DB marker, transitive (depth 1).
    private void level1(Order order) {
        level0(order);
    }

    // Expect: DB marker, direct.
    private void level0(Order order) {
        repository.save(order);
    }
}
