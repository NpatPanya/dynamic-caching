//package com.bbl.cache.examples;
//
//import com.bbl.cache.registry.CacheRegistry;
//import com.bbl.cache.support.DataFilter;
//
//import java.util.List;
//import java.util.Map;
//
///** Simplest string-keyed raw-data registry and filtering usage. */
//public final class SimpleCacheRegistryExample {
//
//    private static final String SETTINGS = "example-settings";
//    private static final String ACTIVE_CUSTOMERS = "example-active-customers";
//    private static final String ACTIVE_BY_ID = "example-active-by-id";
//    private static final String ACTIVE_BY_COUNTRY_AND_ID =
//            "example-active-by-country-and-id";
//
//    private SimpleCacheRegistryExample() {
//    }
//
//    public static void main(String[] args) {
//        CacheRegistry registry = CacheRegistry.getInstance();
//        List<Customer> customers = List.of(
//                new Customer(1001L, "TH", true),
//                new Customer(1002L, "TH", false),
//                new Customer(1003L, "SG", true));
//
//        registry.register(SETTINGS, Map.of("theme", "dark", "language", "en"));
//        registry.register(
//                ACTIVE_CUSTOMERS,
//                DataFilter.filterToList(customers, Customer::active));
//        registry.register(
//                ACTIVE_BY_ID,
//                DataFilter.filterToMap(customers, Customer::active, Customer::id));
//        registry.register(
//                ACTIVE_BY_COUNTRY_AND_ID,
//                DataFilter.filterToNestedMap(
//                        customers, Customer::active, Customer::country, Customer::id));
//
//        Map<String, String> settings = registry.get(SETTINGS);
//        Map<Long, Customer> activeById = registry.get(ACTIVE_BY_ID);
//        String theme = settings.get("theme");
//        Customer customer = activeById.get(1001L);
//        System.out.println("Theme: " + theme + ", customer: " + customer.id());
//
//        registry.remove(SETTINGS);
//        registry.remove(ACTIVE_CUSTOMERS);
//        registry.remove(ACTIVE_BY_ID);
//        registry.remove(ACTIVE_BY_COUNTRY_AND_ID);
//    }
//
//    private record Customer(long id, String country, boolean active) {
//    }
//}