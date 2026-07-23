package com.bbl.cache.registry;

import com.bbl.common.cache.temp.support.CacheFactory;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A cache implementation that stores values using a two-level key structure.
 *
 * <p>The cache internally maintains data in the following form:
 *
 * <pre>
 * Map<K1, Map<K2, V>>
 * </pre>
 *
 * <p>This cache is intended for data that is naturally grouped by a primary
 * key and uniquely identified by a secondary key within that group.
 *
 * <p>Example:
 *
 * <pre>
 * serviceName
 *     -> providerId
 *         -> ServiceProviderProfile
 * </pre>
 *
 * <p>or
 *
 * <pre>
 * serviceName + clientId
 *     -> providerId + responseCode
 *         -> ResponseMapping
 * </pre>
 *
 * @param <K1> primary grouping key
 * @param <K2> secondary unique key within a group
 * @param <V> cached value type
 */
public final class DoubleKeyCache<K1, K2, V>
        extends AbstractMapCache<K1, Map<K2, V>> {

    public DoubleKeyCache(String cacheName, Logger logger) {
        super(cacheName, logger);
    }

    /**
     * Builds (but does not publish) a new cache snapshot using a two-level
     * key structure.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K1, Map<K2, V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *   <li>{@code key1Extractor} determines the outer grouping key</li>
     *   <li>{@code key2Extractor} determines the inner unique key</li>
     *   <li>the original object is stored as the cached value</li>
     * </ul>
     *
     * <p>Duplicate combinations of {@code K1} and {@code K2} are not allowed.
     * If multiple elements resolve to the same key pair, cache construction
     * fails with an {@link IllegalStateException}.
     *
     * @param collection source data used to populate the cache
     * @param key1Extractor extractor for the outer grouping key
     * @param key2Extractor extractor for the inner unique key
     * @return the built, unpublished snapshot
     */
    public Map<K1, Map<K2, V>> stage(
            Collection<V> collection,
            Function<V, K1> key1Extractor,
            Function<V, K2> key2Extractor) {

        validate(collection, key1Extractor);
        Objects.requireNonNull(key2Extractor, "key2Extractor must not be null");

        return CacheFactory.doubleKeysCache(collection, key1Extractor, key2Extractor);
    }

    /**
     * Builds (but does not publish) a new cache snapshot using a two-level
     * key structure and a custom value extractor.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K1, Map<K2, V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *   <li>{@code key1Extractor} determines the outer grouping key</li>
     *   <li>{@code key2Extractor} determines the inner unique key</li>
     *   <li>{@code valueExtractor} determines the cached value</li>
     * </ul>
     *
     * <p>This overload is useful when the source element type differs from
     * the cached value type or when only a subset of the source object is
     * required at runtime.
     *
     * <p>Duplicate combinations of {@code K1} and {@code K2} are not
     * allowed. If multiple elements resolve to the same key pair,
     * cache construction fails with an {@link IllegalStateException}.
     *
     * @param collection source data used to populate the cache
     * @param key1Extractor extractor for the outer grouping key
     * @param key2Extractor extractor for the inner unique key
     * @param valueExtractor extractor for the cached value
     * @param <T> source element type
     * @return the built, unpublished snapshot
     */
    public <T> Map<K1, Map<K2, V>> stage(
            Collection<T> collection,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor,
            Function<T, V> valueExtractor) {

        validate(collection, key1Extractor);

        Objects.requireNonNull(
                key2Extractor,
                "key2Extractor must not be null");

        Objects.requireNonNull(
                valueExtractor,
                "valueExtractor must not be null");

        return CacheFactory.doubleKeysCache(
                collection,
                key1Extractor,
                key2Extractor,
                valueExtractor);
    }

    /**
     * Publishes a previously staged snapshot, atomically replacing the
     * current cache content, and emits the load trace line.
     *
     * @param snapshot the snapshot built by {@link #stage}
     */
    public void publish(Map<K1, Map<K2, V>> snapshot) {
        super.publish(snapshot);
        logLoad();
    }

    /**
     * Loads and replaces the entire cache content using a two-level key structure.
     *
     * <p>Convenience method equivalent to
     * {@code publish(stage(collection, key1Extractor, key2Extractor))}.
     *
     * <p>Existing cache content is atomically replaced after the new cache
     * structure has been successfully built.
     *
     * @param collection source data used to populate the cache
     * @param key1Extractor extractor for the outer grouping key
     * @param key2Extractor extractor for the inner unique key
     */
    public void load(
            Collection<V> collection,
            Function<V, K1> key1Extractor,
            Function<V, K2> key2Extractor) {
        publish(stage(collection, key1Extractor, key2Extractor));
    }

    /**
     * Loads and replaces the entire cache content using a two-level key
     * structure and a custom value extractor.
     *
     * <p>Convenience method equivalent to
     * {@code publish(stage(collection, key1Extractor, key2Extractor, valueExtractor))}.
     *
     * <p>Existing cache content is atomically replaced after the new cache
     * structure has been successfully built.
     *
     * @param collection source data used to populate the cache
     * @param key1Extractor extractor for the outer grouping key
     * @param key2Extractor extractor for the inner unique key
     * @param valueExtractor extractor for the cached value
     * @param <T> source element type
     */
    public <T> void load(
            Collection<T> collection,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor,
            Function<T, V> valueExtractor) {
        publish(stage(collection, key1Extractor, key2Extractor, valueExtractor));
    }


    /**
     * Retrieves a value using both cache keys.
     *
     * <p>Performs a lookup equivalent to:
     *
     * <pre>
     * storedCache.get(key1).get(key2)
     * </pre>
     *
     * <p>If either the outer key or inner key does not exist,
     * {@code null} is returned.
     *
     * @param key1 primary grouping key
     * @param key2 secondary unique key
     * @return matching cached value or {@code null} when no mapping exists
     */
    public V get(K1 key1, K2 key2) {
        Map<K2, V> innerMap = storedCache.get(key1);

        V result = (innerMap == null) ? null : innerMap.get(key2);

        if (logger().isTraceEnabled()) {
            logger().trace("[{}] GET key1={} key2={} hit={}", cacheName(), key1, key2, result != null);
        }
        return result;
    }

    @Override
    public Map<K2, V> get(K1 key1) {
        return storedCache.getOrDefault(key1, Map.of());
    }

    private void logLoad() {
        if (logger().isTraceEnabled()) {
            int totalItem = storedCache.values().stream().mapToInt(Map::size).sum();

            logger().trace("[{}] Loaded {} outer keys and {} cache entries", cacheName(), storedCache.size(), totalItem);
        }
    }
}
