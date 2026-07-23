package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.UniqueCache;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Deprecated
class UniqueCacheTest {

    private record Item(String id, String value) {
    }

    private org.apache.logging.log4j.core.Logger coreLogger;
    private ListTestAppender appender;

    @BeforeEach
    void setUp() {
        String loggerName = "UniqueCacheTest." + UUID.randomUUID();
        coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(loggerName);
        appender = new ListTestAppender("appender-" + loggerName);
        appender.start();
        coreLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        coreLogger.removeAppender(appender);
        appender.stop();
    }

    @Test
    void loadThenAsMapSizeMatchesInputCount_andGetReturnsLoadedValue() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);

        List<Item> items = List.of(
                new Item("a", "alpha"),
                new Item("b", "beta"),
                new Item("c", "gamma"));

        cache.load(items, Item::id);

        assertEquals(3, cache.asMap().size());
        assertEquals(new Item("b", "beta"), cache.get("b"));
    }

    @Test
    void stageThrowsIllegalStateExceptionOnDuplicateKey() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);

        List<Item> items = List.of(
                new Item("a", "alpha"),
                new Item("a", "duplicate"));

        assertThrows(IllegalStateException.class, () -> cache.stage(items, Item::id));
    }

    @Test
    void loadThrowsIllegalStateExceptionOnDuplicateKey() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);

        List<Item> items = List.of(
                new Item("a", "alpha"),
                new Item("a", "duplicate"));

        assertThrows(IllegalStateException.class, () -> cache.load(items, Item::id));
    }

    @Test
    void stageDoesNotMutateExistingPublishedStateOnDuplicateKeyFailure() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);
        cache.load(List.of(new Item("a", "alpha")), Item::id);

        List<Item> dupes = List.of(new Item("a", "1"), new Item("a", "2"));
        assertThrows(IllegalStateException.class, () -> cache.load(dupes, Item::id));

        // previous snapshot must be untouched
        assertEquals(1, cache.asMap().size());
        assertEquals(new Item("a", "alpha"), cache.get("a"));
    }

    @Test
    void asMapReturnsImmutableView() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);
        cache.load(List.of(new Item("a", "alpha")), Item::id);

        Map<String, Item> view = cache.asMap();
        assertThrows(UnsupportedOperationException.class, () -> view.put("b", new Item("b", "beta")));
    }

    @Test
    void getEmitsTraceLineOnlyWhenTraceEnabled() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);
        cache.load(List.of(new Item("a", "alpha")), Item::id);

        coreLogger.setLevel(Level.INFO);
        appender.clear();
        cache.get("a");
        assertTrue(appender.events().isEmpty(), "no trace event expected when TRACE disabled");

        coreLogger.setLevel(Level.TRACE);
        appender.clear();
        cache.get("a");
        assertFalse(appender.events().isEmpty(), "trace event expected when TRACE enabled");
    }

    @Test
    void getOrDefaultEmitsTraceLineOnlyWhenTraceEnabled() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);
        cache.load(List.of(new Item("a", "alpha")), Item::id);

        coreLogger.setLevel(Level.INFO);
        appender.clear();
        cache.getOrDefault("missing", new Item("x", "default"));
        assertTrue(appender.events().isEmpty(), "no trace event expected when TRACE disabled");

        coreLogger.setLevel(Level.TRACE);
        appender.clear();
        cache.getOrDefault("missing", new Item("x", "default"));
        assertFalse(appender.events().isEmpty(), "trace event expected when TRACE enabled");
    }

    @Test
    void containsKeyEmitsTraceLineOnlyWhenTraceEnabled() {
        UniqueCache<String, Item> cache = new UniqueCache<>("byId", coreLogger);
        cache.load(List.of(new Item("a", "alpha")), Item::id);

        coreLogger.setLevel(Level.INFO);
        appender.clear();
        cache.containsKey("a");
        assertTrue(appender.events().isEmpty(), "no trace event expected when TRACE disabled");

        coreLogger.setLevel(Level.TRACE);
        appender.clear();
        cache.containsKey("a");
        assertFalse(appender.events().isEmpty(), "trace event expected when TRACE enabled");
    }
}
