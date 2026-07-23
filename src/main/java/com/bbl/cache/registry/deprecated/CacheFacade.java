package com.bbl.cache.registry.deprecated;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.Caches;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Local compatibility coordinator for applications using all three cache
 * shapes. Application-wide discovery belongs to {@link CacheRegistry}.
 *
 * @deprecated Use {@link Caches} to build caches and {@link CacheRegistry}
 *      for discovery. Left in place and functional for existing subclasses.
 */
@Deprecated(since = "1.0-SNAPSHOT")
public final class CacheFacade {

    private final String cacheName;
    private final Logger logger;
    private final Map<String, KeyedCache<?, ?>> components = new LinkedHashMap<>();

    public CacheFacade(String cacheName, Logger logger) {
        this.cacheName = Objects.requireNonNull(cacheName, "cacheName");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public <K, V> UniqueCache<K, V> uniqueCache(String name) {
        return register(name, new UniqueCache<>(name, logger));
    }

    public <K, V> GroupedCache<K, V> groupedCache(String name) {
        return register(name, new GroupedCache<>(name, logger));
    }

    public <K1, K2, V> DoubleKeyCache<K1, K2, V> doubleKeyCache(String name) {
        return register(name, new DoubleKeyCache<>(name, logger));
    }

    public synchronized void clearAll() {
        components.values().forEach(KeyedCache::invalidateAll);
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] cleared {} components", cacheName, components.size());
        }
    }

    public synchronized int totalSize() {
        return components.values().stream()
                .mapToInt(cache -> Math.toIntExact(cache.size()))
                .sum();
    }

    public synchronized Map<String, Integer> sizes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        components.forEach((name, cache) ->
                result.put(name, Math.toIntExact(cache.size())));
        return Collections.unmodifiableMap(result);
    }

    public synchronized Set<String> cachesName() {
        return Set.copyOf(components.keySet());
    }

    public synchronized KeyedCache<?, ?> get(String name) {
        return components.get(name);
    }

    private synchronized <C extends KeyedCache<?, ?>> C register(String name, C cache) {
        Objects.requireNonNull(name, "name");
        if (components.putIfAbsent(name, cache) != null) {
            throw new IllegalStateException("Duplicate cache component name: " + name);
        }
        return cache;
    }
}
