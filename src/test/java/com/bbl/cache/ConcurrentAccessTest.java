package com.bbl.cache;

import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke-level concurrency test. ConcurrentHashMap's own thread-safety is JDK-verified; this
 * proves the ConcurrentMapCache wrapper doesn't introduce a check-then-act race or unsynchronized
 * field on top of it.
 */
class ConcurrentAccessTest {

    @Test
    void concurrentGetsAndPuts_produceConsistentFinalState() throws InterruptedException {
        int preloadedCount = 50;
        int threadCount = 16;
        int putsPerThread = 20;

        List<UserDto> preloaded = IntStream.range(0, preloadedCount)
                .mapToObj(i -> new UserDto("existing-" + i, "user" + i + "@example.com"))
                .collect(Collectors.toList());

        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> preloaded)
                .buildAndLoad();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < threadCount; t++) {
            int threadIndex = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadIndex % 2 == 0) {
                        for (int i = 0; i < preloadedCount; i++) {
                            cache.get("existing-" + i);
                        }
                    } else {
                        for (int i = 0; i < putsPerThread; i++) {
                            String key = "new-" + threadIndex + "-" + i;
                            cache.put(key, new UserDto(key, key + "@example.com"));
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "threads did not finish in time");
        executor.shutdown();

        assertEquals(0, failures.get());

        int writerThreads = threadCount / 2;
        int expectedSize = preloadedCount + (writerThreads * putsPerThread);
        assertEquals(expectedSize, cache.size());
    }
}
