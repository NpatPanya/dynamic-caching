package com.bbl.cache.examples;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.support.DataFilter;

import java.util.List;
import java.util.Map;

/** Strict assignment typing with a reusable string registry name. */
public final class TypeSafeCacheRegistryExample {

    private static final String CUSTOMERS = "example-typed-customers";

    private TypeSafeCacheRegistryExample() {
    }

    public static void main(String[] args) {
        CacheRegistry registry = CacheRegistry.getInstance();
        List<Customer> customers = List.of(
                new Customer(1001L, "Alice"),
                new Customer(1002L, "Bob"));

        registry.register(
                CUSTOMERS,
                DataFilter.filterToMap(customers, ignored -> true, Customer::id));

        Map<Long, Customer> customersById = registry.get(CUSTOMERS);
        Customer customer = customersById.get(1002L);
        System.out.println("Customer: " + customer.name());

        registry.unregister(CUSTOMERS);
    }

    public record Customer(long id, String name) {
    }
}