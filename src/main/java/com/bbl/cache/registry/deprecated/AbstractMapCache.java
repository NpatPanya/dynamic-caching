package com.bbl.cache.registry.deprecated;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Copy-on-write base for the three snapshot cache shapes. Reads use one
 * volatile immutable snapshot while mutations publish a replacement.
 */
@Deprecated
public sealed abstract class AbstractMapCache<K, V> implements KeyedCache<K, V>
        permits UniqueCache, GroupedCache, DoubleKeyCache {

    private final String cacheName;
    private final Logger logger;
    private final Object mutationLock = new Object();
    protected volatile Map<K, V> storedCache = Map.of();

    protected AbstractMapCache(String cacheName, Logger logger) {
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        V result = storedCache.get(key);
        logItem("GET", key, result);
        return result;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> loader) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(loader, "loader must not be null");
        synchronized (mutationLock) {
            V current = storedCache.get(key);
            if (current != null || storedCache.containsKey(key)) {
                return current;
            }
            V loaded = Objects.requireNonNull(loader.apply(key), "loader result must not be null");
            HashMap<K, V> next = new HashMap<>(storedCache);
            next.put(key, loaded);
            storedCache = Map.copyOf(next);
            logItem("COMPUTE_IF_ABSENT", key, loaded);
            return loaded;
        }
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        synchronized (mutationLock) {
            HashMap<K, V> next = new HashMap<>(storedCache);
            next.put(key, value);
            storedCache = Map.copyOf(next);
        }
        logItem("PUT", key, value);
    }

    @Override
    public void invalidate(K key) {
        Objects.requireNonNull(key, "key must not be null");
        synchronized (mutationLock) {
            if (!storedCache.containsKey(key)) {
                return;
            }
            HashMap<K, V> next = new HashMap<>(storedCache);
            next.remove(key);
            storedCache = Map.copyOf(next);
        }
        logItem("INVALIDATE", key, null);
    }

    @Override
    public void invalidateAll() {
        int previousSize;
        synchronized (mutationLock) {
            previousSize = storedCache.size();
            storedCache = Map.of();
        }
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] Cache cleared, previous size={}", cacheName, previousSize);
        }
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        V result = storedCache.getOrDefault(key, defaultValue);
        logItem("GET_OR_DEFAULT", key, result);
        return result;
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        boolean result = storedCache.containsKey(key);
        logItem("CONTAINS_KEY", key, result);
        return result;
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

    protected static void validate(Collection<?> collection, Function<?, ?> keyExtractor) {
        Objects.requireNonNull(collection, "collection must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
    }

    protected final Logger logger() {
        return logger;
    }

    protected final String cacheName() {
        return cacheName;
    }

    protected void publish(Map<K, V> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        synchronized (mutationLock) {
            storedCache = Map.copyOf(snapshot);
        }
    }

    protected void logItem(String action, Object key, Object value) {
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] {} key={}, value={}", cacheName, action, key, value);
        }
    }
}
