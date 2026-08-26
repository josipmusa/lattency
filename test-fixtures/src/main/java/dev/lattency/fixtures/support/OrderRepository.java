package dev.lattency.fixtures.support;

import org.springframework.data.repository.Repository;

public interface OrderRepository extends Repository<Order, Long> {
    Order save(Order order);
}
