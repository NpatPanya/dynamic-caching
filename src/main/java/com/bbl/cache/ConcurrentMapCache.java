package com.bbl.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** {@link ConcurrentHashMap}-backed {@link Cache}. Only reachable via {@link CacheBuilder}. */
final class ConcurrentMapCache<V> implements Cache<V> {

    private final ConcurrentHashMap<String, V> store;

    ConcurrentMapCache(int initialCapacity) {
        this.store = new ConcurrentHashMap<>(initialCapacity);
    }

    @Override
    public Optional<V> get(String key) {
        return Optional.ofNullable(store.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public V getOrThrow(String key) {
        V value = store.get(Objects.requireNonNull(key, "key"));
        if (value == null) {
            throw new CacheMissException("No value cached for key: " + key);
        }
        return value;
    }

    @Override
    public boolean containsKey(String key) {
        return store.containsKey(key);
    }

    @Override
    public void put(String key, V value) {
        store.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    @Override
    public void putAll(Collection<? extends V> values, KeyExtractor<? super V> keyExtractor) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(keyExtractor, "keyExtractor");
        for (V value : values) {
            String key = keyExtractor.extractKey(value);
            store.put(Objects.requireNonNull(key, "extracted key"), value);
        }
    }

    @Override
    public V remove(String key) {
        return store.remove(key);
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    @Override
    public Collection<V> values() {
        return store.values();
    }

    @Override
    public Map<String, V> asMap() {
        return Collections.unmodifiableMap(store);
    }

    @Override
    public void load(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor) {
        Objects.requireNonNull(loader, "loader");
        putAll(loader.load(), keyExtractor);
    }

    @Override
    public void reload(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor) {
        clear();
        load(loader, keyExtractor);
    }
}
