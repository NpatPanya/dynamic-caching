package com.bbl.cache.factory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class BasicCache<K, V> implements Cache<K, V> {

    protected final Logger log = LogManager.getLogger(getClass());

    protected volatile Map<K, V> storedCache = Map.of();


    protected void load(
            Collection<V> collection,
            Function<V, K> keyExtractor) {

        validate(collection, keyExtractor);

        this.storedCache =
                CacheFactory.from(
                        collection,
                        keyExtractor);

        traceLoaded();
    }

    protected <T> void load(
            Collection<T> collection,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        validate(collection, keyExtractor);
        Objects.requireNonNull(
                valueExtractor,
                "valueExtractor must not be null");

        this.storedCache =
                CacheFactory.from(
                        collection,
                        keyExtractor,
                        valueExtractor);

        traceLoaded();
    }


    @Override
    public V get(K key) {
        return storedCache.get(key);
    }

    @Override
    public V getOrDefault(
            K key,
            V defaultValue) {

        return storedCache.getOrDefault(
                key,
                defaultValue);
    }

    @Override
    public boolean containsKey(K key) {
        return storedCache.containsKey(key);
    }

    @Override
    public int size() {
        return storedCache.size();
    }

    @Override
    public boolean isEmpty() {
        return storedCache.isEmpty();
    }

    @Override
    public Map<K, V> asMap() {
        return storedCache;
    }

    @Override
    public void clear() {

        int currentSize = storedCache.size();

        storedCache = Map.of();

        log.trace(
                "{} cleared {} entries",
                cacheName(),
                currentSize);
    }

    protected String cacheName() {
        return getClass().getSimpleName();
    }

    protected void traceLoaded() {
        log.trace(
                "{} loaded {} entries",
                cacheName(),
                storedCache.size());
    }

    private static void validate(
            Collection<?> collection,
            Function<?, ?> keyExtractor) {

        Objects.requireNonNull(
                collection,
                "collection must not be null");

        Objects.requireNonNull(
                keyExtractor,
                "keyExtractor must not be null");
    }
}