package com.bbl.cache.registry;

import java.util.Map;
import java.util.Objects;

/**
 * Package-private read-only {@link Cache} over an immutable {@link Map}
 * snapshot. This is the concrete implementation {@link Caches} produces.
 *
 * <p>The supplied data is defensively copied via {@link Map#copyOf(Map)} so
 * the resulting cache is immune to later mutation of the source map.
 */
final class ImmutableMapCache<K, V> implements Cache<K, V> {

    private final Map<K, V> data;

    ImmutableMapCache(Map<K, V> data) {
        Objects.requireNonNull(data, "data must not be null");
        this.data = Map.copyOf(data);
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return data.get(key);
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        return data.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return data.containsKey(key);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public Map<K, V> asMap() {
        return data;
    }
}
