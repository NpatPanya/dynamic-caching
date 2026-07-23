package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheFacade}.
 *
 * <p>Tests verify that CacheGroup correctly manages named cache component registration,
 * enforces uniqueness, provides accurate cross-cutting operations, and returns unmodifiable views.
 * Each test focuses on a single acceptance criterion from the design specification (§8).
 */
class CacheFacadeTest {

    private static final Logger logger = LogManager.getLogger(CacheFacadeTest.class);
    private CacheFacade cacheFacade;

    @BeforeEach
    void setUp() {
        cacheFacade = new CacheFacade("TestCacheGroup", logger);
    }

    /**
     * Hook 1: Registering two components under the same name throws IllegalStateException.
     */
    @Test
    @DisplayName("Duplicate name registration within same type throws IllegalStateException")
    void testDuplicateNameSameType() {
        cacheFacade.uniqueCache("testCache");

        assertThrows(IllegalStateException.class, () -> {
            cacheFacade.uniqueCache("testCache");
        }, "Should throw IllegalStateException when registering duplicate name");
    }

    /**
     * Hook 1: Registering two components with the same name (cross-type) also throws.
     * This is a stronger variant: different shape types, same name.
     */
    @Test
    @DisplayName("Duplicate name registration across different types throws IllegalStateException")
    void testDuplicateNameCrossType() {
        cacheFacade.uniqueCache("x");

        assertThrows(IllegalStateException.class, () -> {
            cacheFacade.groupedCache("x");
        }, "Should throw IllegalStateException when registering cross-type duplicate");
    }

    /**
     * Hook 1: Another cross-type variant.
     */
    @Test
    @DisplayName("Duplicate name across all three shapes throws IllegalStateException")
    void testDuplicateNameDoubleKeyCache() {
        cacheFacade.uniqueCache("y");

        assertThrows(IllegalStateException.class, () -> {
            cacheFacade.doubleKeyCache("y");
        }, "Should throw IllegalStateException for double-key duplicate");
    }

    /**
     * Hook 2: clearAll() empties every registered component.
     * Load data into multiple components, verify sizes are non-zero,
     * call clearAll(), then verify all are empty.
     */
    @Test
    @DisplayName("clearAll() clears all registered components")
    void testClearAllEmptiesComponents() {
        // Register and load unique cache
        UniqueCache<String, String> unique = cacheFacade.uniqueCache("unique");
        unique.load(
            Arrays.asList("a", "b", "c"),
            key -> key
        );

        // Register and load grouped cache
        GroupedCache<String, String> grouped = cacheFacade.groupedCache("grouped");
        grouped.load(
            Arrays.asList("x", "y", "x", "z"),
            key -> key.substring(0, 1)
        );

        // Register and load double-key cache
        DoubleKeyCache<String, String, String> doubleKey = cacheFacade.doubleKeyCache("doubleKey");
        doubleKey.load(
            Arrays.asList("item1", "item2", "item3"),
            item -> "outer",  // same outer key
            item -> item
        );

        // Verify components have data before clearing
        assertNotEquals(0, unique.size(), "Unique cache should not be empty before clearAll");
        assertNotEquals(0, grouped.size(), "Grouped cache should not be empty before clearAll");
        assertNotEquals(0, doubleKey.size(), "Double-key cache should not be empty before clearAll");

        int sizeBefore = cacheFacade.totalSize();
        assertTrue(sizeBefore > 0, "Total size should be positive before clearAll");

        // Clear all
        cacheFacade.clearAll();

        // Verify all components are empty
        assertEquals(0, unique.size(), "Unique cache should be empty after clearAll");
        assertEquals(0, grouped.size(), "Grouped cache should be empty after clearAll");
        assertEquals(0, doubleKey.size(), "Double-key cache should be empty after clearAll");
        assertEquals(0, cacheFacade.totalSize(), "Total size should be 0 after clearAll");
    }

    /**
     * Hook 3: totalSize() correctly sums the sizes of registered components.
     *
     * Test design:
     * - UniqueCache with 3 distinct keys → size 3
     * - GroupedCache with 2 distinct keys (4 items grouped) → size 2
     * - DoubleKeyCache with 1 outer key (3 inner items) → size 1
     * Expected totalSize = 3 + 2 + 1 = 6
     */
    @Test
    @DisplayName("totalSize() correctly sums sizes of all components")
    void testTotalSize() {
        // Unique cache: 3 distinct keys
        UniqueCache<String, String> unique = cacheFacade.uniqueCache("unique");
        unique.load(
            Arrays.asList("apple", "banana", "cherry"),
            key -> key
        );
        int uniqueSize = unique.size();
        assertEquals(3, uniqueSize, "Unique cache should have 3 entries");

        // Grouped cache: 4 items under 2 keys
        GroupedCache<String, String> grouped = cacheFacade.groupedCache("grouped");
        grouped.load(
            Arrays.asList("red", "blue", "red", "green"),
            color -> color.equals("red") || color.equals("blue") ? "warm" : "cool"
        );
        int groupedSize = grouped.size();
        assertEquals(2, groupedSize, "Grouped cache should have 2 distinct keys");

        // Double-key cache: 3 items under 1 outer key
        DoubleKeyCache<String, String, String> doubleKey = cacheFacade.doubleKeyCache("doubleKey");
        doubleKey.load(
            Arrays.asList("item1", "item2", "item3"),
            item -> "outer",  // single outer key
            item -> item
        );
        int doubleKeySize = doubleKey.size();
        assertEquals(1, doubleKeySize, "Double-key cache should have 1 outer key");

        // Total should be 3 + 2 + 1 = 6
        int expectedTotal = 3 + 2 + 1;
        assertEquals(expectedTotal, cacheFacade.totalSize(),
            "Total size should be sum of all component sizes");
    }

    /**
     * Hook 3: sizes() returns an accurate map of component name to size.
     */
    @Test
    @DisplayName("sizes() returns correct name-to-size mapping preserving insertion order")
    void testSizes() {
        // Load in insertion order: unique, grouped, doubleKey
        UniqueCache<String, String> unique = cacheFacade.uniqueCache("unique");
        unique.load(
            Arrays.asList("a", "b", "c"),
            key -> key
        );

        GroupedCache<String, String> grouped = cacheFacade.groupedCache("grouped");
        grouped.load(
            Arrays.asList("x", "y", "x"),
            key -> key
        );

        DoubleKeyCache<String, String, String> doubleKey = cacheFacade.doubleKeyCache("doubleKey");
        doubleKey.load(
            Arrays.asList("item1", "item2"),
            item -> "outer",
            item -> item
        );

        Map<String, Integer> sizes = cacheFacade.sizes();

        // Verify all components are present with correct sizes
        assertEquals(3, sizes.size(), "Should have 3 components");
        assertEquals(3, sizes.get("unique"), "unique component should have size 3");
        assertEquals(2, sizes.get("grouped"), "grouped component should have size 2");
        assertEquals(1, sizes.get("doubleKey"), "doubleKey component should have size 1");

        // Verify insertion order is preserved (LinkedHashMap)
        List<String> keys = List.copyOf(sizes.keySet());
        assertEquals(Arrays.asList("unique", "grouped", "doubleKey"), keys,
            "sizes() should preserve insertion order");
    }

    /**
     * Hook 4: names() returns an unmodifiable set.
     * Attempting to mutate it throws UnsupportedOperationException.
     */
    @Test
    @DisplayName("names() returns unmodifiable set - add() throws UnsupportedOperationException")
    void testCachesNameUnmodifiableAdd() {
        cacheFacade.uniqueCache("cache1");
        Set<String> names = cacheFacade.cachesName();

        assertThrows(UnsupportedOperationException.class, () -> {
            names.add("newName");
        }, "Attempting to add to names() set should throw UnsupportedOperationException");
    }

    /**
     * Hook 4: names() unmodifiable - clear() throws UnsupportedOperationException.
     */
    @Test
    @DisplayName("names() returns unmodifiable set - clear() throws UnsupportedOperationException")
    void testCachesNameUnmodifiableClear() {
        cacheFacade.uniqueCache("cache1");
        Set<String> names = cacheFacade.cachesName();

        assertThrows(UnsupportedOperationException.class, () -> {
            names.clear();
        }, "Attempting to clear names() set should throw UnsupportedOperationException");
    }

    /**
     * Hook 4: names() unmodifiable - remove() throws UnsupportedOperationException.
     */
    @Test
    @DisplayName("names() returns unmodifiable set - remove() throws UnsupportedOperationException")
    void testCachesNameUnmodifiableRemove() {
        cacheFacade.uniqueCache("cache1");
        Set<String> names = cacheFacade.cachesName();

        assertThrows(UnsupportedOperationException.class, () -> {
            names.remove("cache1");
        }, "Attempting to remove from names() set should throw UnsupportedOperationException");
    }

    /**
     * Hook 4: names() returns the correct set of registered names.
     */
    @Test
    @DisplayName("names() contains all registered component names")
    void testCachesNameContent() {
        cacheFacade.uniqueCache("cache1");
        cacheFacade.groupedCache("cache2");
        cacheFacade.doubleKeyCache("cache3");

        Set<String> names = cacheFacade.cachesName();

        assertEquals(3, names.size(), "Should have 3 registered names");
        assertTrue(names.contains("cache1"), "names should contain 'cache1'");
        assertTrue(names.contains("cache2"), "names should contain 'cache2'");
        assertTrue(names.contains("cache3"), "names should contain 'cache3'");
    }

    /**
     * Hook 5: get(name) returns the exact same instance that was registered.
     * Uses assertSame to verify instance identity, not just equality.
     */
    @Test
    @DisplayName("get(name) returns the exact same UniqueCache instance")
    void testGetSameInstanceUnique() {
        UniqueCache<String, String> original = cacheFacade.uniqueCache("myUnique");
        KeyedCache<?, ?> retrieved = cacheFacade.get("myUnique");

        assertSame(original, retrieved,
            "get() should return the exact same instance registered");
    }

    /**
     * Hook 5: get(name) returns the exact same instance for GroupedCache.
     */
    @Test
    @DisplayName("get(name) returns the exact same GroupedCache instance")
    void testGetSameInstanceGrouped() {
        GroupedCache<String, String> original = cacheFacade.groupedCache("myGrouped");
        KeyedCache<?, ?> retrieved = cacheFacade.get("myGrouped");

        assertSame(original, retrieved,
            "get() should return the exact same instance registered");
    }

    /**
     * Hook 5: get(name) returns the exact same instance for DoubleKeyCache.
     */
    @Test
    @DisplayName("get(name) returns the exact same DoubleKeyCache instance")
    void testGetSameInstanceDoubleKey() {
        DoubleKeyCache<String, String, String> original = cacheFacade.doubleKeyCache("myDoubleKey");
        KeyedCache<?, ?> retrieved = cacheFacade.get("myDoubleKey");

        assertSame(original, retrieved,
            "get() should return the exact same instance registered");
    }

    /**
     * Hook 5: get(name) returns null for non-existent component.
     */
    @Test
    @DisplayName("get(name) returns null for non-existent component")
    void testGetNonExistent() {
        cacheFacade.uniqueCache("existing");
        KeyedCache<?, ?> result = cacheFacade.get("nonExistent");

        assertNull(result,
            "get() should return null for a non-registered component name");
    }

    /**
     * Integration test: multiple operations in sequence.
     * Register multiple components, verify they all exist, call clearAll,
     * verify all are empty, then register a new component and verify previous are still empty.
     */
    @Test
    @DisplayName("Integration: multiple components with sequential operations")
    void testIntegration() {
        // Register and load
        UniqueCache<String, String> cache1 = cacheFacade.uniqueCache("c1");
        cache1.load(Arrays.asList("a", "b"), key -> key);

        GroupedCache<String, String> cache2 = cacheFacade.groupedCache("c2");
        cache2.load(Arrays.asList("x", "y", "x"), key -> key);

        DoubleKeyCache<String, String, String> cache3 = cacheFacade.doubleKeyCache("c3");
        cache3.load(Arrays.asList("item1"), item -> "k", item -> item);

        // Verify
        assertEquals(3, cacheFacade.cachesName().size());
        assertEquals(5, cacheFacade.totalSize());  // 2 (c1: a,b) + 2 (c2: x,y) + 1 (c3: k) = 5 distinct keys per shape

        // Clear all
        cacheFacade.clearAll();
        assertEquals(0, cacheFacade.totalSize());

        // Verify previously cleared components are still empty
        assertEquals(0, cache1.size());
        assertEquals(0, cache2.size());
        assertEquals(0, cache3.size());

        // Register new component
        UniqueCache<String, String> cache4 = cacheFacade.uniqueCache("c4");
        cache4.load(Arrays.asList("p", "q"), key -> key);

        // Verify previous components are still empty, new one has data
        assertEquals(0, cache1.size());
        assertEquals(0, cache2.size());
        assertEquals(0, cache3.size());
        assertEquals(2, cache4.size());

        // Verify get() works for all
        assertSame(cache1, cacheFacade.get("c1"));
        assertSame(cache2, cacheFacade.get("c2"));
        assertSame(cache3, cacheFacade.get("c3"));
        assertSame(cache4, cacheFacade.get("c4"));
    }
}
