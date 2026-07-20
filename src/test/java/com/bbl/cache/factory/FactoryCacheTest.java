package com.bbl.cache.factory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FactoryCacheTest {

    static class SimpleBasicCache extends BasicCache<String, String> {
    }

    static class SimpleGroupedCache extends GroupedCache<String, String> {
    }

    @Test
    void basicCacheGetPresentKey() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana", "cherry"), s -> s);
        assertEquals("apple", cache.get("apple"));
    }

    @Test
    void basicCacheGetAbsentKey() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana"), s -> s);
        assertNull(cache.get("missing"));
    }

    @Test
    void basicCacheGetOrDefault() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana"), s -> s);
        assertEquals("default", cache.getOrDefault("missing", "default"));
        assertEquals("apple", cache.getOrDefault("apple", "default"));
    }

    @Test
    void basicCacheAsMapUnmodifiable() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana"), s -> s);
        Map<String, String> map = cache.asMap();
        assertThrows(UnsupportedOperationException.class, () -> map.put("new", "value"));
    }

    @Test
    void basicCacheClearEmpties() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana"), s -> s);
        assertEquals(2, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.isEmpty());
    }

    @Test
    void basicCacheDuplicateKeyThrows() {
        SimpleBasicCache cache = new SimpleBasicCache();
        assertThrows(
                IllegalStateException.class,
                () -> cache.load(List.of("apple", "apple"), s -> s),
                "Should throw IllegalStateException for duplicate keys"
        );
    }

    @Test
    void basicCacheNullCollectionThrows() {
        SimpleBasicCache cache = new SimpleBasicCache();
        assertThrows(NullPointerException.class, () -> cache.load(null, s -> s));
    }

    @Test
    void basicCacheNullKeyExtractorThrows() {
        SimpleBasicCache cache = new SimpleBasicCache();
        assertThrows(NullPointerException.class, () -> cache.load(List.of("apple"), null));
    }

    @Test
    void basicCacheNullValueExtractorThrows() {
        SimpleBasicCache cache = new SimpleBasicCache();
        assertThrows(
                NullPointerException.class,
                () -> cache.load(List.of("apple"), s -> s, null)
        );
    }

    @Test
    void groupedCacheGetAbsentReturnsEmptyList() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "banana"), s -> s.substring(0, 1));
        List<String> result = cache.get("missing");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void groupedCacheGetPresentReturnsAllElements() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "apricot", "banana"), s -> s.substring(0, 1));
        List<String> aItems = cache.get("a");
        assertEquals(2, aItems.size());
        assertTrue(aItems.contains("apple"));
        assertTrue(aItems.contains("apricot"));
    }

    @Test
    void groupedCacheSameKeyGroupsIntoMultiElementList() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "apricot"), s -> s.substring(0, 1));
        List<String> aItems = cache.get("a");
        assertEquals(2, aItems.size());
    }

    @Test
    void groupedCacheAsMapOuterUnmodifiable() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "banana"), s -> s.substring(0, 1));
        Map<String, List<String>> map = cache.asMap();
        assertThrows(UnsupportedOperationException.class, () -> map.put("c", List.of("cherry")));
    }

    @Test
    void groupedCacheGetReturnsUnmodifiableList() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "apricot"), s -> s.substring(0, 1));
        List<String> aItems = cache.get("a");
        assertThrows(UnsupportedOperationException.class, () -> aItems.add("avocado"));
    }

    @Test
    void groupedCacheDuplicateKeyThrows() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        // groupedFrom doesn't throw on duplicates by design (groups them together)
        // but let's test that groups are created correctly
        cache.load(List.of("apple", "apricot"), s -> s.substring(0, 1));
        assertEquals(2, cache.get("a").size());
    }

    @Test
    void groupedCacheNullCollectionThrows() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        assertThrows(NullPointerException.class, () -> cache.load(null, s -> s));
    }

    @Test
    void groupedCacheNullKeyExtractorThrows() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        assertThrows(NullPointerException.class, () -> cache.load(List.of("apple"), null));
    }

    @Test
    void basicCacheWithTransformation() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(
                List.of("apple", "banana", "cherry"),
                s -> s.substring(0, 1),
                s -> s.toUpperCase()
        );
        assertEquals("APPLE", cache.get("a"));
        assertEquals("BANANA", cache.get("b"));
    }

    @Test
    void groupedCacheWithTransformation() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(
                List.of("apple", "apricot", "banana"),
                s -> s.substring(0, 1),
                String::toUpperCase
        );
        List<String> aItems = cache.get("a");
        assertEquals(2, aItems.size());
        assertTrue(aItems.contains("APPLE"));
        assertTrue(aItems.contains("APRICOT"));
    }

    @Test
    void basicCacheSize() {
        SimpleBasicCache cache = new SimpleBasicCache();
        cache.load(List.of("apple", "banana", "cherry"), s -> s);
        assertEquals(3, cache.size());
        assertTrue(cache.containsKey("apple"));
        assertFalse(cache.containsKey("missing"));
    }

    @Test
    void groupedCacheSize() {
        SimpleGroupedCache cache = new SimpleGroupedCache();
        cache.load(List.of("apple", "apricot", "banana"), s -> s.substring(0, 1));
        assertEquals(2, cache.size());
        assertTrue(cache.containsKey("a"));
        assertTrue(cache.containsKey("b"));
    }
}
