package com.bbl.cache.registry.deprecated;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.Caches;
import com.bbl.cache.support.CacheFactory;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Two-level cache whose generic cache entry is one immutable inner map.
 *
 * @deprecated Use {@link Caches#doubleKeyedFromList(java.util.List, Function, Function)}
 *      (or its value-extractor overload) together with {@link CacheRegistry}
 *      instead of subclassing. Left in place and functional for existing
 *      subclasses.
 */
@Deprecated(since = "1.0-SNAPSHOT")
public final class DoubleKeyCache<K1, K2, V>
        extends AbstractMapCache<K1, Map<K2, V>> {

    public DoubleKeyCache(String cacheName, Logger logger) {
        super(cacheName, logger);
    }

    public Map<K1, Map<K2, V>> stage(
            Collection<V> collection,
            Function<V, K1> key1Extractor,
            Function<V, K2> key2Extractor) {
        validate(collection, key1Extractor);
        Objects.requireNonNull(key2Extractor, "key2Extractor must not be null");
        return CacheFactory.doubleKeysCache(collection, key1Extractor, key2Extractor);
    }

    public <T> Map<K1, Map<K2, V>> stage(
            Collection<T> collection,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor,
            Function<T, V> valueExtractor) {
        validate(collection, key1Extractor);
        Objects.requireNonNull(key2Extractor, "key2Extractor must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        return CacheFactory.doubleKeysCache(
                collection, key1Extractor, key2Extractor, valueExtractor);
    }

    public void publish(Map<K1, Map<K2, V>> snapshot) {
        super.publish(snapshot);
        logLoad();
    }

    public void load(
            Collection<V> collection,
            Function<V, K1> key1Extractor,
            Function<V, K2> key2Extractor) {
        publish(stage(collection, key1Extractor, key2Extractor));
    }

    public <T> void load(
            Collection<T> collection,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor,
            Function<T, V> valueExtractor) {
        publish(stage(collection, key1Extractor, key2Extractor, valueExtractor));
    }

    public V get(K1 key1, K2 key2) {
        Objects.requireNonNull(key1, "key1 must not be null");
        Objects.requireNonNull(key2, "key2 must not be null");
        Map<K2, V> inner = storedCache.get(key1);
        V result = inner == null ? null : inner.get(key2);
        logItem("GET", key1 + "/" + key2, result);
        return result;
    }

    @Override
    public Map<K2, V> get(K1 key1) {
        Objects.requireNonNull(key1, "key1 must not be null");
        Map<K2, V> result = storedCache.getOrDefault(key1, Map.of());
        logItem("GET", key1, result);
        return result;
    }

    private void logLoad() {
        if (logger().isTraceEnabled()) {
            int items = storedCache.values().stream().mapToInt(Map::size).sum();
            logger().trace("[{}] Loaded {} outer keys and {} cache entries",
                    cacheName(), storedCache.size(), items);
        }
    }
}
