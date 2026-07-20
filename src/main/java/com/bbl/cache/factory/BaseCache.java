package com.bbl.cache.factory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class BaseCache<K, V> {

    private static final Logger log = LogManager.getLogger(BaseCache.class);

    protected volatile Map<K, V> storedCache = Map.of();


    protected void load(Collection<V> collection, Function<V, K> keyExtractor) {
        Objects.requireNonNull(collection, "Collection must not be null");
        Objects.requireNonNull(keyExtractor, "Key extractor function must not be null");
        this.storedCache = CacheFactory.from(collection, keyExtractor);
        log.trace("{} load from {} entires", getClass(), storedCache.size());
    }

    protected <T> void load(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        Objects.requireNonNull(collection, "Collection must not be null");
        Objects.requireNonNull(keyExtractor, "Key extractor function must not be null");
        Objects.requireNonNull(valueExtractor, "Value extractor function must not be null");
        this.storedCache = CacheFactory.from(collection, keyExtractor, valueExtractor);
        log.trace("{} load from {} entires", getClass(), storedCache.size());
    }

    public V get(K key) {
        return storedCache.get(key);
    }

    public V getOrDefault(K key, V defaultValue) {
        return storedCache.getOrDefault(key, defaultValue);
    }

    public boolean containsKey(K key) {
        return storedCache.containsKey(key);
    }

    public int size() {
        return storedCache.size();
    }

    public Map<K, V> asMap() {
        return storedCache;
    }

    public void clear() {
        log.trace("{} clear cache from {} entires", getClass(), storedCache.size());
        this.storedCache = Map.of();

    }


}
