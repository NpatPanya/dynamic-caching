package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.GroupedCache;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupedCacheTest {

    private record Item(String group, String value) {
    }

    private static GroupedCache<String, Item> newCache() {
        String loggerName = "GroupedCacheTest." + UUID.randomUUID();
        return new GroupedCache<>("byGroup", LogManager.getLogger(loggerName));
    }

    @Test
    void multipleValuesUnderSameKeyLandInOneImmutableList() {
        GroupedCache<String, Item> cache = newCache();

        List<Item> items = List.of(
                new Item("g1", "a"),
                new Item("g1", "b"),
                new Item("g2", "c"));

        cache.load(items, Item::group);

        List<Item> g1 = cache.get("g1");
        assertEquals(2, g1.size());
        assertTrue(g1.contains(new Item("g1", "a")));
        assertTrue(g1.contains(new Item("g1", "b")));

        assertThrows(UnsupportedOperationException.class, () -> g1.add(new Item("g1", "c")));
    }

    @Test
    void getOnMissingKeyReturnsEmptyListNeverNull() {
        GroupedCache<String, Item> cache = newCache();
        cache.load(List.of(new Item("g1", "a")), Item::group);

        List<Item> result = cache.get("missing");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getOnMissingKeyReturnsEmptyListWhenCacheNeverLoaded() {
        GroupedCache<String, Item> cache = newCache();

        List<Item> result = cache.get("anything");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

}
