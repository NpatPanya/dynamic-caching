package com.bbl.cache.examples;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.RegistryKey;
import com.bbl.cache.support.DataFilter;

import java.util.List;
import java.util.Map;

/** Simplest typed raw-data registry and filtering usage. */
public final class SimpleCacheRegistryExample {

    private static final RegistryKey<Map<String, String>> SETTINGS =
            RegistryKey.map("example-settings", String.class, String.class);
    private static final RegistryKey<List<Customer>> ACTIVE_CUSTOMERS =
            RegistryKey.list("example-active-customers", Customer.class);
    private static final RegistryKey<Map<Long, Customer>> ACTIVE_BY_ID =
            RegistryKey.map("example-active-by-id", Long.class, Customer.class);
    private static final RegistryKey<Map<String, Map<Long, Customer>>> ACTIVE_BY_COUNTRY_AND_ID =
            RegistryKey.nestedMap(
                    "example-active-by-country-and-id", String.class, Long.class, Customer.class);

    private SimpleCacheRegistryExample() {
    }

    public static void main(String[] args) {
        CacheRegistry registry = CacheRegistry.getInstance();
        List<Customer> customers = List.of(
                new Customer(1001L, "TH", true),
                new Customer(1002L, "TH", false),
                new Customer(1003L, "SG", true));

        registry.register(SETTINGS, Map.of("theme", "dark", "language", "en"));
        registry.register(
                ACTIVE_CUSTOMERS,
                DataFilter.filterToList(customers, Customer::active));
        registry.register(
                ACTIVE_BY_ID,
                DataFilter.filterToMap(customers, Customer::active, Customer::id));
        registry.register(
                ACTIVE_BY_COUNTRY_AND_ID,
                DataFilter.filterToNestedMap(
                        customers, Customer::active, Customer::country, Customer::id));

        String theme = registry.get(SETTINGS).orElseThrow().get("theme");
        Customer customer = registry.get(ACTIVE_BY_ID).orElseThrow().get(1001L);
        System.out.println("Theme: " + theme + ", customer: " + customer.id());

        registry.unregister(SETTINGS);
        registry.unregister(ACTIVE_CUSTOMERS);
        registry.unregister(ACTIVE_BY_ID);
        registry.unregister(ACTIVE_BY_COUNTRY_AND_ID);
    }

    private record Customer(long id, String country, boolean active) {
    }
}