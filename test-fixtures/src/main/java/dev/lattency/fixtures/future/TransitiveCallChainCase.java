package dev.lattency.fixtures.future;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

public final class TransitiveCallChainCase {
    private final OrderRepository repository;

    public TransitiveCallChainCase(OrderRepository repository) {
        this.repository = repository;
    }

    // lattency-future: topLevel should inherit DB through middle and bottom.
    public void topLevel(Order order) {
        middle(order);
    }

    private void middle(Order order) {
        bottom(order);
    }

    private void bottom(Order order) {
        repository.save(order);
    }
}
