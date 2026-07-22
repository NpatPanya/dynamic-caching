package com.bbl.cache.registry;

import com.bbl.cache.support.CacheFactory;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
public final class GroupedCache<K, V>
        extends AbstractMapCache<K, List<V>> {

    public GroupedCache(String cacheName, Logger logger) {
        super(cacheName, logger);
    }

    /**
     * Builds (but does not publish) a new cache snapshot from the supplied
     * collection.
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
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     * @return the built, unpublished snapshot
     */
    public Map<K, List<V>> stage(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        return CacheFactory.groupCache(collection, keyExtractor);
    }

    /**
     * Builds (but does not publish) a new cache snapshot using independently
     * extracted keys and values.
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
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     * @param valueExtractor function used to derive the cached value
     * @param <T> source collection element type
     * @return the built, unpublished snapshot
     */
    public <T> Map<K, List<V>> stage(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        return CacheFactory.groupCache(collection, keyExtractor, valueExtractor);
    }

    /**
     * Publishes a previously staged snapshot, atomically replacing the
     * current cache content, and emits the load trace line.
     *
     * @param snapshot the snapshot built by {@link #stage}
     */
    public void publish(Map<K, List<V>> snapshot) {
        super.publish(snapshot);
        logLoad();
    }

    /**
     * Loads and replaces the entire cache using the supplied collection.
     *
     * <p>Convenience method equivalent to {@code publish(stage(collection, keyExtractor))}.
     *
     * <p>Existing cache content is replaced only after the new cache has
     * been successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     */
    public void load(Collection<V> collection, Function<V, K> keyExtractor) {
        publish(stage(collection, keyExtractor));
    }

    /**
     * Loads and replaces the entire cache using independently extracted
     * keys and values.
     *
     * <p>Convenience method equivalent to
     * {@code publish(stage(collection, keyExtractor, valueExtractor))}.
     *
     * <p>Existing cache content is replaced only after the new cache has
     * been successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the grouping key
     * @param valueExtractor function used to derive the cached value
     * @param <T> source collection element type
     */
    public <T> void load(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        publish(stage(collection, keyExtractor, valueExtractor));
    }

    /**
     * Retrieves all values associated with the supplied key.
     *
     * <p>If the key does not exist, an empty immutable list is returned.
     *
     * <p>This method never returns {@code null}.
     *
     * @param key grouping key
     * @return immutable list of values associated with the key,
     *         or an empty list when no mapping exists
     */
    @Override
    public List<V> get(K key) {
        return storedCache.getOrDefault(key, List.of());
    }

    private void logLoad() {
        if (logger().isTraceEnabled()) {
            logger().trace("[{}] Loaded {} cache entries", cacheName(), storedCache.size());
        }
    }
}
