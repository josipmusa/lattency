package dev.lattency.fixtures.future;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

public final class LazyAssociationCase {
    // lattency-future: customerName may trigger a query through the LAZY getter.
    public String customerName(Purchase purchase) {
        return purchase.getCustomer().getName();
    }

    @Entity
    public static class Purchase {
        @Id private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Customer customer;

        public Customer getCustomer() {
            return customer;
        }
    }

    @Entity
    public static class Customer {
        @Id private Long id;
        private String name;

        public String getName() {
            return name;
        }
    }
}
