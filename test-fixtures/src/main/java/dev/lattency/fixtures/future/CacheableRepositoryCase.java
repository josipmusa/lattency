package dev.lattency.fixtures.future;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;
import org.springframework.cache.annotation.Cacheable;

public final class CacheableRepositoryCase {
    private final OrderRepository repository;

    public CacheableRepositoryCase(OrderRepository repository) {
        this.repository = repository;
    }

    // lattency-future: represent this repository access as conditional I/O.
    @Cacheable("orders")
    public Order find(Order order) {
        return repository.save(order);
    }
}
