package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.DoubleKeyCache;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Deprecated
class DoubleKeyCacheTest {

    private record Item(String outer, String inner, String value) {
    }

    private static DoubleKeyCache<String, String, Item> newCache() {
        String loggerName = "DoubleKeyCacheTest." + UUID.randomUUID();
        return new DoubleKeyCache<>("byOuterInner", LogManager.getLogger(loggerName));
    }

    @Test
    void getWithBothKeysReturnsCorrectValueForLoadedPair() {
        DoubleKeyCache<String, String, Item> cache = newCache();

        List<Item> items = List.of(
                new Item("o1", "i1", "v1"),
                new Item("o1", "i2", "v2"),
                new Item("o2", "i1", "v3"));

        cache.load(items, Item::outer, Item::inner);

        assertEquals(new Item("o1", "i2", "v2"), cache.get("o1", "i2"));
        assertEquals(new Item("o2", "i1", "v3"), cache.get("o2", "i1"));
    }

    @Test
    void getWithBothKeysReturnsNullWhenPairMissing() {
        DoubleKeyCache<String, String, Item> cache = newCache();
        cache.load(List.of(new Item("o1", "i1", "v1")), Item::outer, Item::inner);

        assertNull(cache.get("o1", "missing"));
        assertNull(cache.get("missingOuter", "i1"));
    }

    @Test
    void getWithSingleKeyOnMissingOuterKeyReturnsEmptyMapNotNull() {
        DoubleKeyCache<String, String, Item> cache = newCache();
        cache.load(List.of(new Item("o1", "i1", "v1")), Item::outer, Item::inner);

        Map<String, Item> result = cache.get("missingOuter");
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(Map.of(), result);
    }

    @Test
    void getWithSingleKeyReturnsInnerMapForKnownOuterKey() {
        DoubleKeyCache<String, String, Item> cache = newCache();

        List<Item> items = List.of(
                new Item("o1", "i1", "v1"),
                new Item("o1", "i2", "v2"));

        cache.load(items, Item::outer, Item::inner);

        Map<String, Item> inner = cache.get("o1");
        assertEquals(2, inner.size());
        assertEquals(new Item("o1", "i1", "v1"), inner.get("i1"));
    }

    @Test
    void stageThrowsIllegalStateExceptionOnDuplicatePair() {
        DoubleKeyCache<String, String, Item> cache = newCache();

        List<Item> items = List.of(
                new Item("o1", "i1", "v1"),
                new Item("o1", "i1", "v2-duplicate-pair"));

        assertThrows(IllegalStateException.class, () -> cache.stage(items, Item::outer, Item::inner));
    }

    @Test
    void loadThrowsIllegalStateExceptionOnDuplicatePair() {
        DoubleKeyCache<String, String, Item> cache = newCache();

        List<Item> items = List.of(
                new Item("o1", "i1", "v1"),
                new Item("o1", "i1", "v2-duplicate-pair"));

        assertThrows(IllegalStateException.class, () -> cache.load(items, Item::outer, Item::inner));
    }

    @Test
    void loadDoesNotMutateExistingStateOnDuplicatePairFailure() {
        DoubleKeyCache<String, String, Item> cache = newCache();
        cache.load(List.of(new Item("o1", "i1", "v1")), Item::outer, Item::inner);

        List<Item> dupes = List.of(
                new Item("o2", "i1", "x1"),
                new Item("o2", "i1", "x2-duplicate-pair"));

        assertThrows(IllegalStateException.class, () -> cache.load(dupes, Item::outer, Item::inner));

        assertEquals(1, cache.asMap().size());
        assertEquals(new Item("o1", "i1", "v1"), cache.get("o1", "i1"));
    }
}
