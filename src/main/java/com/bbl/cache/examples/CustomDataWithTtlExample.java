package com.bbl.cache.examples;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.support.DataFilter;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Registers an immutable string-keyed snapshot with a per-registration TTL. */
public final class CustomDataWithTtlExample {

    private static final String CUSTOMERS = "example-customers";

    private CustomDataWithTtlExample() {
    }

    public static void main(String[] args) {
        CacheRegistry registry = CacheRegistry.getInstance();
        List<Customer> customers = List.of(
                new Customer(1001L, "Alice"),
                new Customer(1002L, "Bob"));

        registry.register(
                CUSTOMERS,
                DataFilter.filterToMap(customers, ignored -> true, Customer::id),
                Duration.ofMinutes(10));

        Map<Long, Customer> customersById = registry.get(CUSTOMERS);
        Customer customer = customersById.get(1001L);
        System.out.println("Customer: " + customer.name());

        registry.unregister(CUSTOMERS);
    }

    public record Customer(long id, String name) {
    }
}