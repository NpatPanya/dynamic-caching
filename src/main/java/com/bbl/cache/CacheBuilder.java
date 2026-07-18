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
 *
 * <p>Instead of a lambda, {@link #withKeyField(String)} lets you name the field/getter to key by
 * (e.g. {@code "id"}, {@code "customerId"}) and have it read via reflection at load time — handy
 * when the key field varies by configuration rather than by code path.
 */
public final class CacheBuilder<V> {

    private KeyExtractor<? super V> keyExtractor;
    private CacheLoader<V> loader;
    private int initialCapacity = 16;

    private CacheBuilder() {
    }

    /** Starts building a new cache of value type {@code V}. */
    public static <V> CacheBuilder<V> newBuilder() {
        return new CacheBuilder<>();
    }

    /**
     * Sets the mapping from a cached value to its String key, required by {@link #buildAndLoad()}
     * (not by {@link #build()} — a bare cache populated only via {@link Cache#put} never needs one).
     */
    public CacheBuilder<V> withKeyExtractor(KeyExtractor<? super V> keyExtractor) {
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
        return this;
    }

    /**
     * Keys by the named field/getter, resolved via reflection at load time, instead of a
     * hand-written {@link KeyExtractor}. See {@link FieldKeyExtractor} for the resolution order.
     */
    public CacheBuilder<V> withKeyField(String fieldName) {
        return withKeyExtractor(FieldKeyExtractor.of(fieldName));
    }

    /**
     * Sets the data source used by {@link #buildAndLoad()}, or by a later manual
     * {@link Cache#load}/{@link Cache#reload} call — required by {@link #buildAndLoad()} only.
     */
    public CacheBuilder<V> withLoader(CacheLoader<V> loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        return this;
    }

    /** Sizing hint for the backing store; purely a performance tweak, never required. Default 16. */
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

    /**
     * Builds the cache and synchronously populates it via the configured loader/key extractor.
     *
     * @throws CacheConfigurationException if {@link #withKeyExtractor}/{@link #withKeyField} or
     *                                       {@link #withLoader} was never called
     */
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
