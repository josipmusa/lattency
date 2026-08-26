package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

public final class CallSiteCase {
    private final OrderRepository repository;

    public CallSiteCase(OrderRepository repository) {
        this.repository = repository;
    }

    // Expect: DB marker, direct; the repository.save line also gets a call-site icon.
    public Order persist(Order order) {
        return repository.save(order);
    }

    // Expect: DB marker, transitive; the persist(order) line gets a call-site icon.
    public Order indirect(Order order) {
        return persist(order);
    }
}
