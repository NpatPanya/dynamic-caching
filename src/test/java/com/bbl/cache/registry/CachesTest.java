package com.bbl.cache.registry;

import com.bbl.cache.support.ViewFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Factory-transform coverage for {@link Caches} (design v3 deliverable #7):
 * list / map / filtered-list -> correct shape, and duplicate-key ->
 * {@link IllegalStateException} preserved unchanged from
 * {@link com.bbl.cache.support.CacheFactory}.
 */
class CachesTest {

    private record Item(String id, String category, String value) {
    }

    private static List<Item> sampleData() {
        return List.of(
                new Item("1", "fruit", "alpha"),
                new Item("2", "fruit", "beta"),
                new Item("3", "veggie", "gamma"));
    }

    @Test
    void fromListBuildsUniqueCacheByExtractedKey() {
        Cache<String, Item> cache = Caches.fromList(sampleData(), Item::id);

        assertEquals(3, cache.size());
        assertEquals(new Item("1", "fruit", "alpha"), cache.get("1"));
    }

    @Test
    void fromListWithValueExtractorBuildsUniqueCacheOfExtractedValue() {
        Cache<String, String> cache = Caches.fromList(sampleData(), Item::id, Item::value);

        assertEquals(3, cache.size());
        assertEquals("beta", cache.get("2"));
    }

    @Test
    void fromListThrowsIllegalStateExceptionOnDuplicateKey() {
        List<Item> dupes = List.of(
                new Item("1", "fruit", "alpha"),
                new Item("1", "fruit", "duplicate"));

        assertThrows(IllegalStateException.class, () -> Caches.fromList(dupes, Item::id));
    }

    @Test
    void fromMapWrapsAlreadyKeyedDataWithoutReKeying() {
        Map<String, Item> source = Map.of(
                "a", new Item("a", "fruit", "alpha"),
                "b", new Item("b", "veggie", "beta"));

        Cache<String, Item> cache = Caches.fromMap(source);

        assertEquals(2, cache.size());
        assertEquals(source.get("a"), cache.get("a"));
        assertTrue(cache.asMap() instanceof Map);
    }

    @Test
    void groupedFromListGroupsByExtractedKey() {
        Cache<String, List<Item>> cache = Caches.groupedFromList(sampleData(), Item::category);

        assertEquals(2, cache.size());
        assertEquals(2, cache.get("fruit").size());
        assertEquals(1, cache.get("veggie").size());
    }

    @Test
    void groupedFromListWithValueExtractorGroupsExtractedValues() {
        Cache<String, List<String>> cache =
                Caches.groupedFromList(sampleData(), Item::category, Item::value);

        assertEquals(List.of("alpha", "beta"), cache.get("fruit"));
    }

    @Test
    void doubleKeyedFromListBuildsTwoLevelMap() {
        Cache<String, Map<String, Item>> cache =
                Caches.doubleKeyedFromList(sampleData(), Item::category, Item::id);

        assertEquals(2, cache.size());
        assertEquals(new Item("1", "fruit", "alpha"), cache.get("fruit").get("1"));
    }

    @Test
    void doubleKeyedFromListWithValueExtractorBuildsTwoLevelMapOfExtractedValues() {
        Cache<String, Map<String, String>> cache =
                Caches.doubleKeyedFromList(sampleData(), Item::category, Item::id, Item::value);

        assertEquals("alpha", cache.get("fruit").get("1"));
    }

    @Test
    void doubleKeyedFromListThrowsIllegalStateExceptionOnDuplicateKeyPair() {
        List<Item> dupes = List.of(
                new Item("1", "fruit", "alpha"),
                new Item("1", "fruit", "beta"));

        assertThrows(IllegalStateException.class,
                () -> Caches.doubleKeyedFromList(dupes, Item::category, Item::id));
    }

    @Test
    void filteredListViaViewFactoryThenIndexedByCachesComposesCorrectly() {
        List<Item> fruitsOnly = ViewFactory.filteredView(sampleData(), item -> "fruit".equals(item.category()));
        Cache<String, Item> cache = Caches.fromList(fruitsOnly, Item::id);

        assertEquals(2, cache.size());
        assertEquals("alpha", cache.get("1").value());
        assertEquals(null, cache.get("3"));
    }
}
