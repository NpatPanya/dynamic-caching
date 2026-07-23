package com.bbl.cache.registry;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Application-wide registry for typed immutable data snapshots. */
public final class CacheRegistry {

    private final ConcurrentHashMap<String, StringValueEntry> registry = new ConcurrentHashMap<>();
    private final LongSupplier ticker;

    private CacheRegistry() {
        this(System::nanoTime);
    }

    CacheRegistry(LongSupplier ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    }

    public static CacheRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public <T> T register(String name, T data) {
        return registerValue(name, data, Optional.empty(), false);
    }

    public <T> T register(String name, T data, Duration ttl) {
        return registerValue(name, data, validatedTtl(ttl), false);
    }

    public <T> T reregister(String name, T data) {
        return registerValue(name, data, Optional.empty(), true);
    }

    public <T> T reregister(String name, T data, Duration ttl) {
        return registerValue(name, data, validatedTtl(ttl), true);
    }

    public <T> T get(String name) {
        return this.<T>find(name).orElseThrow(
                () -> new NoSuchElementException("No registry value named: " + name));
    }

    public <T> Optional<T> find(String name) {
        validateName(name);
        StringValueEntry entry = registry.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(ticker.getAsLong())) {
            registry.remove(name, entry);
            return Optional.empty();
        }
        return Optional.of(expose(entry));
    }

    public boolean unregister(String name) {
        validateName(name);
        return registry.remove(name) != null;
    }

    public void invalidateAll() {
        registry.clear();
    }

    private <T> T registerValue(
            String name, T data, Optional<Duration> ttl, boolean replace) {
        validateName(name);
        Objects.requireNonNull(data, "data must not be null");
        T snapshot = SnapshotSupport.snapshot(data);
        StringValueEntry candidate = new StringValueEntry(
                snapshot,
                ticker.getAsLong(),
                ttl.map(Duration::toNanos),
                SnapshotSupport.requiresDefensiveCopy(snapshot));
        if (!replace) {
            if (registry.putIfAbsent(name, candidate) != null) {
                throw new IllegalStateException("Registry name already registered: " + name);
            }
        } else {
            registry.put(name, candidate);
        }
        return SnapshotSupport.expose(snapshot, candidate.defensiveCopyOnRead());
    }

    @SuppressWarnings("unchecked")
    private static <T> T expose(StringValueEntry entry) {
        return SnapshotSupport.expose((T) entry.data(), entry.defensiveCopyOnRead());
    }

    private static Optional<Duration> validatedTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        try {
            if (ttl.toNanos() == Long.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "ttl must be less than Long.MAX_VALUE nanoseconds");
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("ttl is too large", ex);
        }
        return Optional.of(ttl);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache name must not be blank");
        }
    }

    private record StringValueEntry(
            Object data,
            long registeredAt,
            Optional<Long> ttlNanos,
            boolean defensiveCopyOnRead) {

        private StringValueEntry {
            Objects.requireNonNull(data, "data must not be null");
            Objects.requireNonNull(ttlNanos, "ttlNanos must not be null");
        }

        private boolean isExpired(long now) {
            return ttlNanos.isPresent() && now - registeredAt > ttlNanos.orElseThrow();
        }
    }

    private static final class Holder {
        private static final CacheRegistry INSTANCE = new CacheRegistry();
    }
}