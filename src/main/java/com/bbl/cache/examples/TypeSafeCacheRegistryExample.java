package com.bbl.cache.examples;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.RegistryKey;
import com.bbl.cache.support.DataFilter;

import java.util.List;
import java.util.Map;

/** Exact generic retrieval through a shared RegistryKey. */
public final class TypeSafeCacheRegistryExample {

    private static final RegistryKey<Map<Long, Customer>> CUSTOMERS =
            RegistryKey.map("example-typed-customers", Long.class, Customer.class);

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

        Customer customer = registry.get(CUSTOMERS)
                .orElseThrow()
                .get(1002L);
        System.out.println("Customer: " + customer.name());

        registry.unregister(CUSTOMERS);
    }

    public record Customer(long id, String name) {
    }
}