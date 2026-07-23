package com.bbl.cache.support;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataFilterTest {

    private record Customer(long id, String country, String name, boolean active) {
    }

    private final List<Customer> customers = List.of(
            new Customer(1L, "TH", "A", true),
            new Customer(2L, "TH", "B", false),
            new Customer(3L, "SG", "C", true),
            new Customer(4L, "US", "D", false)
    );

    @Test
    void filterToListPreservesEncounterOrderAndReturnsAnImmutableList() {
        List<Customer> active =
                DataFilter.filterToList(customers, Customer::active);

        assertEquals(List.of(customers.get(0), customers.get(2)), active);
        assertThrows(UnsupportedOperationException.class, () -> active.add(customers.get(1)));
    }

    @Test
    void filterToMapCanKeepTheSourceObjectOrExtractAnotherValue() {
        Map<Long, Customer> activeById =
                DataFilter.filterToMap(customers, Customer::active, Customer::id);
        Map<Long, String> activeNamesById =
                DataFilter.filterToMap(
                        customers, Customer::active, Customer::id, Customer::name);

        assertEquals(Map.of(1L, customers.get(0), 3L, customers.get(2)), activeById);
        assertEquals(Map.of(1L, "A", 3L, "C"), activeNamesById);
        assertThrows(UnsupportedOperationException.class,
                () -> activeNamesById.put(4L, "D"));
    }

    @Test
    void filterToNestedMapCanKeepTheSourceObjectOrExtractAnotherValue() {
        Map<String, Map<Long, Customer>> activeByCountryAndId =
                DataFilter.filterToNestedMap(
                        customers, Customer::active, Customer::country, Customer::id);
        Map<String, Map<Long, String>> activeNamesByCountryAndId =
                DataFilter.filterToNestedMap(
                        customers,
                        Customer::active,
                        Customer::country,
                        Customer::id,
                        Customer::name);
        Map<Long, Map<String, Customer>> thAndUsCategorization = DataFilter.filterToNestedMap(
                customers,
                c -> (c.country.equals("TH") || c.country.equals("US")),
                Customer::id,
                Customer::country);

        assertEquals(Map.of(1L, Map.of(customers.get(0).country, customers.get(0)),2L, Map.of(customers.get(1).country, customers.get(1)),4L, Map.of(customers.get(3).country, customers.get(3))), thAndUsCategorization);

        assertEquals(
                Map.of(
                        "TH", Map.of(1L, customers.get(0)),
                        "SG", Map.of(3L, customers.get(2))),
                activeByCountryAndId);
        assertEquals(
                Map.of("TH", Map.of(1L, "A"), "SG", Map.of(3L, "C")),
                activeNamesByCountryAndId);
        assertThrows(UnsupportedOperationException.class,
                () -> activeNamesByCountryAndId.get("TH").put(4L, "D"));
        assertThrows(UnsupportedOperationException.class,
                () -> activeNamesByCountryAndId.put("JP", Map.of()));
    }

    @Test
    void mapShapesRejectDuplicateKeysInsteadOfOverwriting() {
        List<Customer> duplicates = List.of(
                new Customer(1L, "TH", "A", true),
                new Customer(1L, "TH", "B", true));

        assertThrows(IllegalStateException.class,
                () -> DataFilter.filterToMap(
                        duplicates, Customer::active, Customer::id));
        assertThrows(IllegalStateException.class,
                () -> DataFilter.filterToNestedMap(
                        duplicates, Customer::active, Customer::country, Customer::id));
    }

    @Test
    void nullResultsFromExtractorsAreRejectedBeforeRegistration() {
        assertThrows(IllegalArgumentException.class,
                () -> DataFilter.filterToMap(
                        customers, Customer::active, ignored -> null));
        assertThrows(IllegalArgumentException.class,
                () -> DataFilter.filterToNestedMap(
                        customers, Customer::active, Customer::country, ignored -> null));
    }

    @Test
    void filterToListRejectsSelectedNullElements() {
        assertThrows(IllegalArgumentException.class,
                () -> DataFilter.filterToList(
                        Collections.singletonList(null), ignored -> true));
    }
}
