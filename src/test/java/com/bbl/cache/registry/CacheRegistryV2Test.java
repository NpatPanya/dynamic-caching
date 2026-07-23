package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.UniqueCache;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class CacheRegistryV2Test {

    private final CacheRegistry registry = CacheRegistry.getInstance();

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        registry.clear();
    }

    @Test
    void singletonUsesOneIdentity() {
        assertSame(CacheRegistry.getInstance(), CacheRegistry.getInstance());
    }

    @Test
    void noTtlRegistrationReturnsDelegateAndNeverExpires() {
        AtomicLong simulatedTime = new AtomicLong();
        Cache<String, String> delegate = Caches.fromMap(Map.of("key", "value"));

        Cache<String, String> cache = registry.register("forever", delegate);
        simulatedTime.addAndGet(Duration.ofDays(3650).toNanos());

        assertSame(delegate, cache);
        assertEquals("value", cache.get("key"));
        assertEquals("value", registry.getCache("forever").orElseThrow().get("key"));
    }

    @Test
    void ttlRegistrationReturnsDecoratorAndRetrievalReturnsSameDecorator() {
        UniqueCache<String, String> delegate = newCache("ttl");

        Cache<String, String> registered =
                registry.register("ttl", delegate, Duration.ofMinutes(1));

        assertNotSame(delegate, registered);
        assertTrue(registered instanceof TtlCache);
        assertSame(registered, registry.getCache("ttl").orElseThrow());
    }

    @Test
    void duplicateRegistrationRaceHasExactlyOneWinner() throws Exception {
        int racers = 24;
        ExecutorService executor = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Cache<String, String>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                int candidate = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return registry.register("race", newCache("candidate-" + candidate));
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            int duplicates = 0;
            Cache<String, String> winner = null;
            for (Future<Cache<String, String>> future : futures) {
                try {
                    winner = future.get(5, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException ex) {
                    assertTrue(ex.getCause() instanceof IllegalStateException);
                    duplicates++;
                }
            }

            assertEquals(1, successes);
            assertEquals(racers - 1, duplicates);
            assertSame(winner, registry.getCache("race").orElseThrow());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unregisterDoesNotCorruptAnInFlightReader() throws Exception {
        Cache<String, String> cache =
                registry.register("in-flight", Caches.fromMap(Map.of("key", "value")));
        CountDownLatch obtainedReference = new CountDownLatch(1);
        CountDownLatch continueRead = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> read = executor.submit(() -> {
                Cache<Object, Object> acquired =
                        registry.getCache("in-flight").orElseThrow();
                obtainedReference.countDown();
                assertTrue(continueRead.await(5, TimeUnit.SECONDS));
                return (String) acquired.get("key");
            });

            assertTrue(obtainedReference.await(5, TimeUnit.SECONDS));
            assertTrue(registry.unregister("in-flight"));
            continueRead.countDown();

            assertEquals("value", read.get(5, TimeUnit.SECONDS));
            assertEquals(Optional.empty(), registry.getCache("in-flight"));
            assertFalse(registry.unregister("in-flight"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validatesNamesTtlAndDuplicatePolicy() {
        UniqueCache<String, String> delegate = newCache("validation");

        assertThrows(IllegalArgumentException.class, () -> registry.register(" ", delegate));
        assertThrows(NullPointerException.class, () -> registry.register("null", null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("zero", delegate, Duration.ZERO));

        registry.register("duplicate", delegate);
        assertThrows(IllegalStateException.class,
                () -> registry.register("duplicate", newCache("other")));
    }

    @Test
    void descriptorRegistrationAndRetrievalReturnsTypedCache() {
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("descriptor-key", String.class, String.class);
        Cache<String, String> cache = Caches.fromMap(Map.of("key", "value"));

        registry.register(descriptor, cache);

        Cache<String, String> retrieved = registry.get(descriptor).orElseThrow();
        assertSame(cache, retrieved);
        assertEquals("value", retrieved.get("key"));
    }

    @Test
    void typeMismatchRetrievalThrowsIllegalStateExceptionNamingExpectedVsActual() {
        CacheDescriptor<String, String> registered =
                CacheDescriptor.of("mismatch-key", String.class, String.class);
        CacheDescriptor<String, Integer> requested =
                CacheDescriptor.of("mismatch-key", String.class, Integer.class);

        registry.register(registered, Caches.fromMap(Map.of("key", "value")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.get(requested));
        assertTrue(ex.getMessage().contains("mismatch-key"));
        assertTrue(ex.getMessage().contains(String.class.getName()));
        assertTrue(ex.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void stringRegisteredEntryRetrievedViaDescriptorThrowsIllegalStateException() {
        registry.register("untyped-key", Caches.fromMap(Map.of("key", "value")));
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("untyped-key", String.class, String.class);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> registry.get(descriptor));
        assertTrue(ex.getMessage().contains("untyped-key"));
    }

    @Test
    void descriptorRetrievalOfAbsentKeyReturnsEmpty() {
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("absent-key", String.class, String.class);
        assertEquals(Optional.empty(), registry.get(descriptor));
    }

    @Test
    void reregisterUpsertsWithoutPriorRegistration() {
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("reload-key", String.class, String.class);
        Cache<String, String> fresh = Caches.fromMap(Map.of("key", "v1"));

        Cache<String, String> result = registry.reregister(descriptor, fresh);

        assertSame(fresh, result);
        assertEquals("v1", registry.get(descriptor).orElseThrow().get("key"));
    }

    @Test
    void reregisterAtomicallyReplacesExistingEntry() {
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("reload-key-2", String.class, String.class);
        registry.register(descriptor, Caches.fromMap(Map.of("key", "v1")));

        Cache<String, String> replacement = Caches.fromMap(Map.of("key", "v2"));
        registry.reregister(descriptor, replacement);

        Cache<String, String> retrieved = registry.get(descriptor).orElseThrow();
        assertSame(replacement, retrieved);
        assertEquals("v2", retrieved.get("key"));
    }

    @Test
    void reregisterViaStringOverloadDropsTypeWitness() {
        CacheDescriptor<String, String> descriptor =
                CacheDescriptor.of("reload-key-3", String.class, String.class);
        registry.register(descriptor, Caches.fromMap(Map.of("key", "v1")));

        registry.reregister("reload-key-3", Caches.fromMap(Map.of("key", "v2")));

        assertThrows(IllegalStateException.class, () -> registry.get(descriptor));
        assertEquals("v2", registry.getCache("reload-key-3").orElseThrow().get("key"));
    }

    @Test
    void deprecatedStringGetRetainsItsGenericSourceCompatibility() {
        registry.register("legacy-generic", Caches.fromMap(Map.of("key", "value")));

        Cache<String, String> retrieved =
                registry.<String, String>getCache("legacy-generic").orElseThrow();

        assertEquals("value", retrieved.get("key"));
    }
    private static <K, V> UniqueCache<K, V> newCache(String name) {
        return new UniqueCache<>(name, LogManager.getLogger("CacheRegistryV2Test." + name));
    }
}
