package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

public final class TransitiveCallChainCase {
    private final OrderRepository repository;

    public TransitiveCallChainCase(OrderRepository repository) {
        this.repository = repository;
    }

    // Expect: DB marker, transitive (depth 2), chain topLevel -> middle -> bottom -> save.
    public void topLevel(Order order) {
        middle(order);
    }

    // Expect: DB marker, transitive (depth 1).
    private void middle(Order order) {
        bottom(order);
    }

    // Expect: DB marker, direct.
    private void bottom(Order order) {
        repository.save(order);
    }
}
