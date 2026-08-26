package dev.lattency.fixtures;

import dev.lattency.fixtures.support.Order;
import dev.lattency.fixtures.support.OrderRepository;
import org.springframework.cache.annotation.Cacheable;

public final class CacheableRepositoryCase {
    private final OrderRepository repository;

    public CacheableRepositoryCase(OrderRepository repository) {
        this.repository = repository;
    }

    // Expect: DB marker, direct - the body itself always hits the repository when run.
    @Cacheable("orders")
    public Order find(Order order) {
        return repository.save(order);
    }

    // Expect: GENERIC marker with a conditional (@Cacheable) edge to find; the walk
    // stops at the cacheable callee, so no DB category is claimed here.
    public Order lookup(Order order) {
        return find(order);
    }
}
