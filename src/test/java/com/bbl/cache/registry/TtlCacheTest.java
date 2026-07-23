package com.bbl.cache.registry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot-level lazy-expiry assertions for {@link TtlCache} (design v3
 * section 1(b)). Every case verifies whole-snapshot expiry with zero
 * delegate mutation: reads before the TTL boundary succeed, reads after
 * return the empty view across all six {@link Cache} methods.
 */
class TtlCacheTest {

    @Test
    void readsBeforeBoundarySucceedAcrossAllSixMethods() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
        TtlCache<String, String> cache = new TtlCache<>(delegate, Duration.ofNanos(10), ticker::get);

        ticker.set(10); // exactly at the boundary: NOT expired (strictly-greater-than check)

        assertEquals("value", cache.get("key"));
        assertEquals("value", cache.getOrDefault("key", "default"));
        assertTrue(cache.containsKey("key"));
        assertEquals(1, cache.size());
        assertFalse(cache.isEmpty());
        assertEquals(Map.of("key", "value"), cache.asMap());
    }

    @Test
    void readsAfterBoundaryReturnEmptyViewAcrossAllSixMethods() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
        TtlCache<String, String> cache = new TtlCache<>(delegate, Duration.ofNanos(10), ticker::get);

        ticker.set(11); // strictly past the boundary: expired

        assertNull(cache.get("key"));
        assertEquals("default", cache.getOrDefault("key", "default"));
        assertFalse(cache.containsKey("key"));
        assertEquals(0, cache.size());
        assertTrue(cache.isEmpty());
        assertEquals(Map.of(), cache.asMap());
    }

    @Test
    void expiryIsZeroDelegateMutation() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
        TtlCache<String, String> cache = new TtlCache<>(delegate, Duration.ofNanos(10), ticker::get);

        ticker.set(100);
        assertNull(cache.get("key"));

        // the delegate snapshot itself is untouched by expiry evaluation
        assertEquals("value", delegate.get("key"));
        assertEquals(1, delegate.size());
    }

    @Test
    void prePopulatedDelegateExpiresFromDecoratorCreationTime() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
        TtlCache<String, String> cache = new TtlCache<>(delegate, Duration.ofNanos(10), ticker::get);

        ticker.set(11);

        assertNull(cache.get("key"));
        // delegate remains untouched -- expiry is a read-time gate on the decorator, not a mutation
        assertEquals(1, delegate.size());
    }

    @Test
    void registryLevelNeverExpiresWithoutTtl() {
        // Never-expire-without-TTL is a CacheRegistry-level behavior: registering
        // without a Duration returns the delegate undecorated (no TtlCache, no gate).
        CacheRegistry registry = CacheRegistry.getInstance();
        registry.clear();
        try {
            Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
            Cache<String, String> registered = registry.register("never-expires", delegate);

            assertEquals(delegate, registered);
            assertEquals("value", registered.get("key"));
        } finally {
            registry.clear();
        }
    }

    @Test
    void rejectsInvalidConstruction() {
        AtomicLong ticker = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));

        assertThrows(IllegalArgumentException.class,
                () -> new TtlCache<>(delegate, Duration.ZERO, ticker::get));
        assertThrows(IllegalArgumentException.class,
                () -> new TtlCache<>(delegate, Duration.ofSeconds(-1), ticker::get));
    }

    @Test
    void publicConstructorUsesSystemNanoTimeTicker() {
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));
        TtlCache<String, String> cache = new TtlCache<>(delegate, Duration.ofMinutes(5));

        // not expired immediately after construction
        assertEquals("value", cache.get("key"));
    }
}
