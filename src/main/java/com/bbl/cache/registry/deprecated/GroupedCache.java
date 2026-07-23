package com.bbl.cache.registry.deprecated;

import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.Caches;
import com.bbl.cache.support.CacheFactory;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * One-to-many cache where each cache entry is an immutable group list.
 *
 * @deprecated Use {@link Caches#groupedFromList(java.util.List, Function)}
 *      (or its value-extractor overload) together with {@link CacheRegistry}
 *      instead of subclassing. Left in place and functional for existing
 *      subclasses.
 */
@Deprecated(since = "1.0-SNAPSHOT")
public final class GroupedCache<K, V> extends AbstractMapCache<K, List<V>> {

    public GroupedCache(String cacheName, Logger logger) {
        super(cacheName, logger);
    }

    public Map<K, List<V>> stage(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        return CacheFactory.groupCache(collection, keyExtractor);
    }

    public <T> Map<K, List<V>> stage(
            Collection<T> collection,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        return CacheFactory.groupCache(collection, keyExtractor, valueExtractor);
    }

    public void publish(Map<K, List<V>> snapshot) {
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

    @Override
    public List<V> get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        List<V> result = storedCache.getOrDefault(key, List.of());
        logItem("GET", key, result);
        return result;
    }

    private void logLoad() {
        if (logger().isTraceEnabled()) {
            int items = storedCache.values().stream().mapToInt(List::size).sum();
            logger().trace("[{}] Loaded {} groups and {} cache entries",
                    cacheName(), storedCache.size(), items);
        }
    }
}
