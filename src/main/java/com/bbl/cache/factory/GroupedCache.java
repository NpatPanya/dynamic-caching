package com.bbl.cache.factory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class GroupedCache<K, V> implements Cache<K, List<V>> {

    protected final Logger log =
            LogManager.getLogger(getClass());

    protected volatile Map<K, List<V>> storedCache =
            Map.of();

    protected void load(
            Collection<V> collection,
            Function<V, K> keyExtractor) {

        validate(collection, keyExtractor);

        this.storedCache =
                CacheFactory.groupedFrom(
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
                CacheFactory.groupedFrom(
                        collection,
                        keyExtractor,
                        valueExtractor);

        traceLoaded();
    }

    @Override
    public List<V> get(K key) {
        return storedCache.getOrDefault(
                key,
                List.of()
        );
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
    public Map<K, List<V>> asMap() {
        return storedCache;
    }


    @Override
    public List<V> getOrDefault(
            K key,
            List<V> defaultValue) {

        return storedCache.getOrDefault(
                key,
                defaultValue);
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
                "{} loaded {} groups",
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