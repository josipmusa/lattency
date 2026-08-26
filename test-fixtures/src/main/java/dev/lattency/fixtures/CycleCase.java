package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

/** Mutual recursion (pingPong -> pong -> pingPong -> save) must terminate and color. */
public final class CycleCase {
    private final OrderRepository repository;

    public CycleCase(OrderRepository repository) {
        this.repository = repository;
    }

    // Expect: DB marker, direct (the cycle edge back from pong adds nothing shorter).
    public void pingPong(Order order) {
        pong(order);
        repository.save(order);
    }

    // Expect: DB marker, transitive (depth 1 through pingPong).
    public void pong(Order order) {
        pingPong(order);
    }
}
