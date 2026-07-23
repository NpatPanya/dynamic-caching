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
 * Single-key cache with atomic immutable-snapshot refresh support.
 *
 * @deprecated Use {@link Caches#fromList(java.util.List, Function)} (or its
 *      value-extractor overload) together with {@link CacheRegistry} instead
 *      of subclassing. Left in place and functional for existing subclasses.
 */
@Deprecated(since = "1.0-SNAPSHOT")
public final class UniqueCache<K, V> extends AbstractMapCache<K, V> {

    public UniqueCache(String cacheName, Logger logger) {
        super(cacheName, logger);
    }

    public Map<K, V> stage(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        return CacheFactory.uniqueCache(collection, keyExtractor);
    }

    public <T> Map<K, V> stage(
            Collection<T> collection,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        return CacheFactory.uniqueCache(collection, keyExtractor, valueExtractor);
    }

    public void publish(Map<K, V> snapshot) {
        super.publish(snapshot);
        logLoad();
    }

    public void load(Collection<V> collection, Function<V, K> keyExtractor) {
        publish(stage(collection, keyExtractor));
    }

    public <T> void load(
            Collection<T> collection,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {
        publish(stage(collection, keyExtractor, valueExtractor));
    }

    private void logLoad() {
        if (logger().isTraceEnabled()) {
            logger().trace("[{}] Loaded {} cache entries", cacheName(), storedCache.size());
        }
    }
}
