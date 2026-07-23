package com.bbl.cache.registry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringKeyCacheRegistryTest {

    private final AtomicLong ticker = new AtomicLong();
    private final CacheRegistry registry = new CacheRegistry(ticker::get);

    @Test
    void registerAndGetInferTheDeclaredAssignmentType() {
        Map<String, String> registered =
                registry.register("settings", Map.of("theme", "dark"));

        Map<String, String> retrieved = registry.get("settings");

        assertEquals(Map.of("theme", "dark"), registered);
        assertEquals("dark", retrieved.get("theme"));
    }

    @Test
    void findReturnsOptionalValueOrEmpty() {
        registry.register("message", "hello");

        Optional<String> present = registry.find("message");
        Optional<String> absent = registry.find("missing");

        assertEquals(Optional.of("hello"), present);
        assertEquals(Optional.empty(), absent);
    }

    @Test
    void getThrowsWhenNameIsMissing() {
        NoSuchElementException error = assertThrows(
                NoSuchElementException.class,
                () -> registry.get("missing"));

        assertEquals("No registry value named: missing", error.getMessage());
    }

    @Test
    void reregisterAtomicallyReplacesTheValueAndMayChangeItsType() {
        registry.register("value", "old");
        String acquiredBeforeReplacement = registry.get("value");

        List<String> replacement = registry.reregister("value", List.of("new"));
        List<String> current = registry.get("value");

        assertEquals("old", acquiredBeforeReplacement);
        assertEquals(List.of("new"), replacement);
        assertEquals(List.of("new"), current);
    }

    @Test
    void reregisterStartsAFreshTtl() {
        registry.register("ttl", "v1", Duration.ofNanos(10));
        ticker.set(11);
        assertEquals(Optional.empty(), registry.find("ttl"));

        registry.reregister("ttl", "v2", Duration.ofNanos(10));
        ticker.set(21);
        assertEquals("v2", registry.get("ttl"));
        ticker.set(22);
        assertEquals(Optional.empty(), registry.find("ttl"));
        assertThrows(NoSuchElementException.class, () -> registry.get("ttl"));
    }

    @Test
    void wrongDeclaredRawTypeThrowsClassCastException() {
        registry.register("number", 42);

        assertThrows(ClassCastException.class, () -> {
            String wrongType = registry.get("number");
            assertEquals("unreachable", wrongType);
        });
    }

    @Test
    void invalidateAllRemovesEveryRegistrationWithoutMutatingAcquiredSnapshots() {
        registry.register("first", Map.of("key", "value"));
        registry.register("second", List.of("value"));
        Map<String, String> acquired = registry.get("first");

        registry.invalidateAll();

        assertEquals(Map.of("key", "value"), acquired);
        assertEquals(Optional.empty(), registry.find("first"));
        assertEquals(Optional.empty(), registry.find("second"));
        registry.register("first", "reused");
        assertEquals("reused", registry.get("first"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void primaryReregisterCanReplaceDeprecatedRegistrations() {
        registry.register("legacy-cache", Caches.fromMap(Map.of("key", "value")));
        RegistryKey<String> typedKey = RegistryKey.value("legacy-key", String.class);
        registry.register(typedKey, "old");

        String cacheReplacement = registry.reregister("legacy-cache", "new-cache-value");
        Integer keyReplacement = registry.reregister("legacy-key", 42);

        assertEquals("new-cache-value", cacheReplacement);
        assertEquals("new-cache-value", registry.get("legacy-cache"));
        assertEquals(42, keyReplacement);
        assertEquals(42, registry.<Integer>get("legacy-key"));
    }

    @Test
    void generalCollectionsAreSnapshottedAndExposedAsUnmodifiableCollections() {
        ArrayDeque<String> source = new ArrayDeque<>(List.of("first"));
        Collection<String> registered =
                registry.register("queue", (Collection<String>) source);

        source.add("source-mutation");
        assertThrows(UnsupportedOperationException.class, () -> registered.add("read-mutation"));

        Collection<String> retrieved = registry.get("queue");
        assertEquals(List.of("first"), new ArrayList<>(retrieved));
    }

    @Test
    void nestedMapsAndListsAreRecursiveSnapshots() {
        List<String> mutableValues = new ArrayList<>(List.of("original"));
        Map<String, List<String>> source = new java.util.LinkedHashMap<>();
        source.put("values", mutableValues);

        Map<String, List<String>> registered = registry.register("nested", source);
        mutableValues.add("source-mutation");

        assertEquals(List.of("original"), registered.get("values"));
        assertThrows(UnsupportedOperationException.class,
                () -> registered.put("other", List.of("value")));
        assertThrows(UnsupportedOperationException.class,
                () -> registered.get("values").add("read-mutation"));
    }

    @Test
    void concurrentExpiredReadCannotDeleteAReplacement() throws Exception {
        AtomicLong time = new AtomicLong();
        AtomicBoolean blockReader = new AtomicBoolean();
        AtomicReference<Thread> readerThread = new AtomicReference<>();
        CountDownLatch readerReachedExpiryCheck = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        CacheRegistry concurrentRegistry = new CacheRegistry(() -> {
            if (blockReader.get() && Thread.currentThread() == readerThread.get()) {
                readerReachedExpiryCheck.countDown();
                try {
                    if (!releaseReader.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("reader was not released");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            return time.get();
        });
        concurrentRegistry.register("expiring", "old", Duration.ofNanos(10));
        time.set(11);
        blockReader.set(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Optional<String>> staleRead = executor.submit(() -> {
                readerThread.set(Thread.currentThread());
                return concurrentRegistry.find("expiring");
            });
            assertTrue(readerReachedExpiryCheck.await(5, TimeUnit.SECONDS));

            concurrentRegistry.reregister("expiring", "replacement");
            releaseReader.countDown();

            assertEquals(Optional.empty(), staleRead.get(5, TimeUnit.SECONDS));
            assertEquals("replacement", concurrentRegistry.get("expiring"));
        } finally {
            releaseReader.countDown();
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void deprecatedCacheReregisterCannotReplaceAPrimaryStringRegistration() {
        registry.register("shared", "primary");

        assertThrows(
                IllegalStateException.class,
                () -> registry.reregister(
                        "shared",
                        Caches.fromMap(Map.of("legacy", "value"))));
        assertEquals("primary", registry.get("shared"));
    }

    @Test
    void nestedArraysAreDefensivelyCopiedForEveryRead() {
        registry.register("arrays", List.of(new int[]{1, 2}));

        List<int[]> firstRead = registry.get("arrays");
        firstRead.get(0)[0] = 99;

        List<int[]> secondRead = registry.get("arrays");
        assertEquals(1, secondRead.get(0)[0]);
    }

    @Test
    void concurrentRegistrationHasExactlyOneWinner() throws Exception {
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
                    return registry.register("race", value);
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
                } catch (ExecutionException error) {
                    assertTrue(error.getCause() instanceof IllegalStateException);
                    duplicates++;
                }
            }

            assertEquals(1, successes);
            assertEquals(racers - 1, duplicates);
        } finally {
            executor.shutdownNow();
        }
    }
}