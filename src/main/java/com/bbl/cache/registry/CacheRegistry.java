package com.bbl.cache.registry;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;


@ApplicationScoped
public class CacheRegistry {

    private final ConcurrentHashMap<String, StringValueEntry> registry = new ConcurrentHashMap<>();

    private final Map<String, LoaderRegistration> loaderMap = new ConcurrentHashMap<>();

    private final LongSupplier ticker;

    public CacheRegistry() {
        this(System::nanoTime);
    }

    CacheRegistry(LongSupplier ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    }

//
//    public <T> void register(CacheLoader<T> cacheLoaders) {
//        loaderMap.put(cacheLoaders.getName(), cacheLoaders);
//        registerValue(cacheLoaders.getName(), cacheLoaders.load(), Optional.empty());
//    }

    public <T> void register(CacheLoader<T> loader) {

        if (loaderMap.putIfAbsent(
                loader.getName(),
                new LoaderRegistration(loader, Optional.empty())) != null) {

            throw new IllegalArgumentException(
                    "Loader already registered: " + loader.getName());
        }

        registerValue(
                loader.getName(),
                loader.load(),
                Optional.empty()
        );
    }


    public <T> void register(
            CacheLoader<T> loader,
            Duration ttl) {

        Optional<Duration> validated = validatedTtl(ttl);


        if (loaderMap.putIfAbsent(
                loader.getName(),
                new LoaderRegistration(loader, validated)) != null) {

            throw new IllegalArgumentException(
                    "Loader already registered: " + loader.getName());
        }

        registerValue(
                loader.getName(),
                loader.load(),
                validated
        );
    }


    public <T> T register(String name, T data) {
        return registerValue(name, data, Optional.empty());
    }

    public <T> T register(String name, T data, Duration ttl) {
        return registerValue(name, data, validatedTtl(ttl));
    }

    public void reloadCacheLoader(String name) {

        LoaderRegistration registration =
                loaderMap.get(name);

        if (registration == null) {
            throw new IllegalArgumentException(
                    "No loader registered: " + name);
        }

        replaceValue(
                name,
                registration.cacheLoader().load(),
                registration.ttl()
        );
    }

    public void reloadAll() {
        loaderMap.keySet()
                .forEach(this::reloadCacheLoader);
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

        loaderMap.remove(name);

        return registry.remove(name) != null;
    }


    public boolean remove(String name) {
        validateName(name);
        return registry.remove(name) != null;
    }

    public void invalidateAll() {
        registry.clear();
    }

    private <T> T registerValue(
            String name, T data, Optional<Duration> ttl) {
        validateName(name);
        Objects.requireNonNull(data, "data must not be null");
        T snapshot = SnapshotSupport.snapshot(data);
        StringValueEntry candidate = new StringValueEntry(
                snapshot,
                ticker.getAsLong(),
                ttl.map(Duration::toNanos),
                SnapshotSupport.requiresDefensiveCopy(snapshot));

        if (registry.putIfAbsent(name, candidate) != null) {
            throw new IllegalArgumentException("Registry key " + name + " already registered");
        }

        return SnapshotSupport.expose(snapshot, candidate.defensiveCopyOnRead());
    }

    private <T> void replaceValue(
            String name,
            T data,
            Optional<Duration> ttl) {

        validateName(name);
        Objects.requireNonNull(data, "data must not be null");
        T snapshot = SnapshotSupport.snapshot(data);

        StringValueEntry candidate =
                new StringValueEntry(
                        snapshot,
                        ticker.getAsLong(),
                        ttl.map(Duration::toNanos),
                        SnapshotSupport.requiresDefensiveCopy(snapshot));

        registry.put(name, candidate);
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

    private record LoaderRegistration(CacheLoader<?> cacheLoader, Optional<Duration> ttl) {

    }

}