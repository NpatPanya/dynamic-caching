package com.bbl.cache.factory;



import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/**
 * A cache implementation that stores values using a single unique key.
 *
 * <p>The cache internally maintains data in the following form:
 *
 * <pre>
 * Map<K, V>
 * </pre>
 *
 * <p>This cache is intended for reference data where each key uniquely
 * identifies a single value.
 *
 * <p>Example:
 *
 * <pre>
 * providerId
 *     -> ProviderProfile
 * </pre>
 *
 * <pre>
 * responseCode
 *     -> ResponseDescription
 * </pre>
 *
 * <p>The cache content is immutable after loading and is atomically
 * replaced whenever a load operation is executed.
 *
 * @param <K> cache key type
 * @param <V> cached value type
 */
public abstract class UniqueCache<K, V> extends AbstractMapCache<K, V> {

    /**
     * Loads and replaces the entire cache using the supplied collection.
     *
     * <p>The provided {@code keyExtractor} is used to derive a unique cache key
     * for each element in the collection. The original element itself becomes
     * the cached value.
     *
     * <p>The resulting cache structure is:
     *
     * <pre>
     * Map<K, V>
     * </pre>
     *
     * <p>Duplicate keys are not permitted. If multiple elements resolve to
     * the same key, cache construction fails with an
     * {@link IllegalStateException}.
     *
     * <p>Existing cache content is replaced only after the new cache has been
     * successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the cache key
     */
    protected void load(
            Collection<V> collection,
            Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        this.storedCache = CacheFactory.uniqueCache(collection, keyExtractor);
        logLoader();
    }

    /**
     * Loads and replaces the entire cache using independently extracted
     * keys and values.
     *
     * <p>This overload allows the source object and cached value to differ.
     *
     * <p>The resulting cache structure is:
     *
     * <pre>
     * Map<K, V>
     * </pre>
     *
     * <p>For each element in the supplied collection:
     * <ul>
     *     <li>{@code keyExtractor} produces the cache key</li>
     *     <li>{@code valueExtractor} produces the cached value</li>
     * </ul>
     *
     * <p>Duplicate keys are not permitted. If multiple elements resolve to
     * the same key, cache construction fails with an
     * {@link IllegalStateException}.
     *
     * <p>Existing cache content is replaced only after the new cache has been
     * successfully constructed.
     *
     * @param collection source data used to populate the cache
     * @param keyExtractor function used to derive the cache key
     * @param valueExtractor function used to derive the cached value
     * @param <T> source collection element type
     */
    protected <T> void load(
            Collection<T> collection,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        this.storedCache = CacheFactory.uniqueCache(collection, keyExtractor, valueExtractor);
        logLoader();
    }

    /**
     * Writes cache loading statistics when TRACE logging is enabled.
     *
     * <p>The log entry contains the cache implementation name and the total
     * number of entries successfully loaded into the cache.
     *
     * <p>This method is intended for operational troubleshooting and cache
     * verification during startup or cache refresh operations.
     */
    private void logLoader() {
        if (logger().isTraceEnabled()) {
            logger().trace("[{}] Loaded {} cache entries", cacheName(), storedCache.size());
        }
    }
}
