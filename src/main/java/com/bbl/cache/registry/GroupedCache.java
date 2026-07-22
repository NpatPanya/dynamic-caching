package com.bbl.cache.registry;

import com.bbl.gw.config.cache.support.CacheFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A cache implementation that stores values using a grouped one-to-many
 * relationship.
 *
 * <p>The cache internally maintains data in the following form:
 *
 * <pre>
 * Map<K, List<V>>
 * </pre>
 *
 * <p>This cache is intended for scenarios where multiple values may be
 * associated with the same cache key.
 *
 * <p>Unlike {@link UniqueCache}, duplicate keys are expected and all values
 * belonging to the same key are retained within an immutable list.
 *
 * <p>Example:
 *
 * <pre>
 * serviceName
 *     -> List<ServiceProviderProfile>
 * </pre>
 *
 * <pre>
 * providerId
 *     -> List<ResponseMapping>
 * </pre>
 *
 * <p>The cache content is immutable after loading and is atomically
 * replaced whenever a load operation is executed.
 *
 * @param <K> grouping key type
 * @param <V> cached value type
 */
public abstract class GroupedCache<K, V>
        extends AbstractMapCache<K, List<V>> {

    /**
     * Loads and replaces the entire cache using the supplied collection.
     *
     * <p>The provided collection is transformed into:
     *
     * <pre>
     * Map<K, List<V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *     <li>{@code keyExtractor} determines the grouping key</li>
     *     <li>the original object is stored as the grouped value</li>
     * </ul>
     *
     * <p>All elements producing the same key are grouped into an immutable
     * list associated with that key.
     *
     * <p>Existing cache content is replaced only after the new cache has
     * been successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     */
    protected void load(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        this.storedCache = CacheFactory.groupCache(collection, keyExtractor);
        logLoader();
    }

    /**
     * Loads and replaces the entire cache using independently extracted
     * keys and values.
     *
     * <p>The provided collection is transformed into:
     *
     * <pre>
     * Map<K, List<V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *     <li>{@code keyExtractor} determines the grouping key</li>
     *     <li>{@code valueExtractor} determines the cached value</li>
     * </ul>
     *
     * <p>All values associated with the same key are grouped into an
     * immutable list.
     *
     * <p>This overload is useful when the source object and cached value
     * differ.
     *
     * <p>Existing cache content is replaced only after the new cache has
     * been successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     * @param valueExtractor function used to derive the cached value
     * @param <T> source collection element type
     */
    protected <T> void load(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        this.storedCache = CacheFactory.groupCache(collection, keyExtractor, valueExtractor);
        logLoader();
    }

    /**
     * Retrieves all values associat*d with the supplied key.
     *
     * <p>*f the key does not exist, an empty*immutable list is returned.
     *
     * *p>This method never returns {@code*null}.
     *
     * @param key grouping k*y
     * @return immutable list of val*es associated with the key,
     *    *    or an empty list when no mappi*g exists
     */
    @Override
    public List<V> get(K key) {
        return storedCache.getOrDefault(key, List.of());
    }

    private void logLoader() {
        if (logger().isTraceEnabled()) {
            logger().trace("[{}] Loaded {} cache entries", cacheName(), storedCache.size());
        }
    }
}
