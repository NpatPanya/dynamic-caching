package com.bbl.cache.registry;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class RawDataCacheRegistryTest {

    private final AtomicLong ticker = new AtomicLong();
    private final CacheRegistry registry = new CacheRegistry(ticker::get);

    @Test
    void typedMapRetrievalPreservesItsExactGenericType() {
        RegistryKey<Map<String, String>> key =
                RegistryKey.map("settings", String.class, String.class);

        registry.register(key, Map.of("theme", "dark"));

        String theme = registry.get(key).orElseThrow().get("theme");
        assertEquals("dark", theme);
    }

    @Test
    void registrationStoresAnImmutableListSnapshot() {
        RegistryKey<List<String>> key = RegistryKey.list("languages", String.class);
        List<String> source = new ArrayList<>(List.of("en"));

        List<String> registered = registry.register(key, source);
        source.add("th");

        List<String> retrieved = registry.get(key).orElseThrow();
        assertEquals(List.of("en"), registered);
        assertEquals(List.of("en"), retrieved);
        assertNotSame(source, retrieved);
        assertThrows(UnsupportedOperationException.class, () -> retrieved.add("jp"));
    }

    @Test
    void anotherKeyObjectCannotAccessOrReplaceAnOccupiedName() {
        RegistryKey<String> registeredKey = RegistryKey.value("shared", String.class);
        RegistryKey<String> reconstructedKey = RegistryKey.value("shared", String.class);
        registry.register(registeredKey, "value");

        assertThrows(IllegalStateException.class, () -> registry.get(reconstructedKey));
        assertThrows(IllegalStateException.class,
                () -> registry.reregister(reconstructedKey, "replacement"));
        assertEquals("value", registry.get(registeredKey).orElseThrow());
    }

    @Test
    void reregisterAtomicallyReplacesOnlyTheSameTypedKey() {
        RegistryKey<Map<String, String>> key =
                RegistryKey.map("reload", String.class, String.class);
        Map<String, String> first = registry.register(key, Map.of("version", "v1"));

        Map<String, String> replacement =
                registry.reregister(key, Map.of("version", "v2"));

        assertEquals("v1", first.get("version"));
        assertEquals("v2", replacement.get("version"));
        assertEquals("v2", registry.get(key).orElseThrow().get("version"));
    }

    @Test
    void expiredRegistrationIsAbsentAndReregisterRefreshesItsTtl() {
        RegistryKey<String> key = RegistryKey.value("ttl", String.class);
        registry.register(key, "v1", Duration.ofNanos(10));

        ticker.set(10);
        assertEquals("v1", registry.get(key).orElseThrow());
        ticker.set(11);
        assertEquals(Optional.empty(), registry.get(key));

        registry.reregister(key, "v2", Duration.ofNanos(10));
        ticker.set(21);
        assertEquals("v2", registry.get(key).orElseThrow());
        ticker.set(22);
        assertEquals(Optional.empty(), registry.get(key));
    }

    @Test
    void typedUnregisterRequiresTheOriginalKey() {
        RegistryKey<String> key = RegistryKey.value("remove", String.class);
        RegistryKey<String> other = RegistryKey.value("remove", String.class);
        registry.register(key, "value");

        assertThrows(IllegalStateException.class, () -> registry.unregister(other));
        assertTrue(registry.unregister(key));
        assertFalse(registry.unregister(key));
    }

    @Test
    void validatesNullDataDurationAndDuplicateRegistration() {
        RegistryKey<String> key = RegistryKey.value("validation", String.class);

        assertThrows(NullPointerException.class, () -> registry.register(key, null));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(key, "value", Duration.ZERO));

        registry.register(key, "value");
        assertThrows(IllegalStateException.class,
                () -> registry.register(key, "duplicate"));
    }
    @Test
    void concurrentTypedRegistrationHasExactlyOneWinner() throws Exception {
        RegistryKey<String> key = RegistryKey.value("race", String.class);
        int racers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                String value = "candidate-" + i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return registry.register(key, value);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            int duplicates = 0;
            for (Future<String> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException ex) {
                    assertTrue(ex.getCause() instanceof IllegalStateException);
                    duplicates++;
                }
            }

            assertEquals(1, successes);
            assertEquals(racers - 1, duplicates);
            assertTrue(registry.get(key).orElseThrow().startsWith("candidate-"));
        } finally {
            executor.shutdownNow();
        }
    }
    @Test
    void arraysNestedInsideCollectionsAreDefensivelyCopiedForEveryRead() {
        RegistryKey<List<int[]>> key = RegistryKey.list("array-list", int[].class);
        registry.register(key, List.of(new int[]{1, 2}));

        List<int[]> firstRead = registry.get(key).orElseThrow();
        firstRead.get(0)[0] = 99;

        assertEquals(1, registry.get(key).orElseThrow().get(0)[0]);
    }
}
