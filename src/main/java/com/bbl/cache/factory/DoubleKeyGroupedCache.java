package com.bbl.cache.factory;

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
public abstract class DoubleKeyGroupedCache<K1, K2, V>
        extends AbstractMapCache<K1, Map<K2, V>> {

    /**
     * Loads and replaces the entire cache content using a two-level key structure.
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
     * <p>Existing cache content is atomically replaced after the new cache
     * structure has been successfully built.
     *
     * @param collection source data used to populate the cache
     * @param key1Extractor extractor for the outer grouping key
     * @param key2Extractor extractor for the inner unique key
     */
    protected void load(
            Collection<V> collection,
            Function<V, K1> key1Extractor,
            Function<V, K2> key2Extractor) {

        validate(collection, key1Extractor);
        Objects.requireNonNull(key2Extractor, "key2Extractor must not be null");

        this.storedCache = CacheFactory.doubleKeysCache(collection, key1Extractor, key2Extractor);

        if(logger().isTraceEnabled()){
            int totalItem = storedCache.values().stream().mapToInt(Map::size).sum();

            logger().trace("[{}] Loaded {} outer keys and {} cache entries",cacheName(),storedCache.size(),totalItem);

        }
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
}