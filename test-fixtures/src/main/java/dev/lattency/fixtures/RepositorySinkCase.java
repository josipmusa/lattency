package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;

public final class RepositorySinkCase {
    private final OrderRepository repository;

    public RepositorySinkCase(OrderRepository repository) {
        this.repository = repository;
    }

    public void save(Order order) {
        repository.save(order);
    }
}
