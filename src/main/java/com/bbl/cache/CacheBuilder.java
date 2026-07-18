package com.bbl.cache;

import java.util.Objects;

/**
 * Fluent assembly point for {@link Cache} instances.
 *
 * <pre>{@code
 * Cache<User> users = CacheBuilder.<User>newBuilder()
 *         .withKeyExtractor(User::getId)
 *         .withLoader(userRepository::findAll)
 *         .buildAndLoad();
 * }</pre>
 */
public final class CacheBuilder<V> {

    private KeyExtractor<? super V> keyExtractor;
    private CacheLoader<V> loader;
    private int initialCapacity = 16;

    private CacheBuilder() {
    }

    public static <V> CacheBuilder<V> newBuilder() {
        return new CacheBuilder<>();
    }

    public CacheBuilder<V> withKeyExtractor(KeyExtractor<? super V> keyExtractor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
        return this;
    }

    public CacheBuilder<V> withLoader(CacheLoader<V> loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        return this;
    }

    public CacheBuilder<V> withInitialCapacity(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be >= 0, was: " + initialCapacity);
        }
        this.initialCapacity = initialCapacity;
        return this;
    }

    /** Builds an empty cache. Use {@link #buildAndLoad()} to populate it immediately at startup. */
    public Cache<V> build() {
        return new ConcurrentMapCache<>(initialCapacity);
    }

    /** Builds the cache and synchronously populates it via the configured loader/key extractor. */
    public Cache<V> buildAndLoad() {
        if (keyExtractor == null) {
            throw new CacheConfigurationException("withKeyExtractor(...) must be set before buildAndLoad()");
        }
        if (loader == null) {
            throw new CacheConfigurationException("withLoader(...) must be set before buildAndLoad()");
        }
        Cache<V> cache = build();
        cache.load(loader, keyExtractor);
        return cache;
    }
}
