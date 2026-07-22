package com.bbl.cache.registry;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class AbstractMapCache<K, S> implements KeyedCache<K, S> {


    protected volatile Map<K, S> storedCache = Map.of();

    @Override
    public S get(K key) {
        S result = storedCache.get(key);
        logItem("GET", key, result);
        return result;
    }

    @Override
    public S getOrDefault(K key, S def) {
        S result = storedCache.getOrDefault(key, def);
        logItem("GET_OR_DEFAULT", key, result);
        return result;
    }

    @Override
    public boolean containsKey(K key) {
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
    public Map<K, S> asMap() {
        return storedCache;
    }

    @Override
    public void clear() {

        int size = 0;
        if (logger().isTraceEnabled()) {
            size = this.storedCache.size();
        }

        this.storedCache = Map.of();

        if (logger().isTraceEnabled()) {
            logger().trace("[{}] Cache cleared, previous clear size ={}", cacheName(), size);
        }
    }

    protected static void validate(Collection<?> collection, Function<?, ?> keyExtractor) {
        Objects.requireNonNull(collection, "collection must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
    }


    protected abstract Logger logger();


    protected String cacheName() {
        return getClass().getSimpleName();
    }

    /**
     * Writes a trace log entry for cache operations.
     *
     * <p>This method is invoked by cache access methods such as
     * {@code get()}, {@code getOrDefault()} and {@code containsKey()}
     * to provide detailed visibility into cache usage when TRACE
     * logging is enabled.
     *
     * <p>Implementations intentionally log cache keys and resolved
     * values to support troubleshooting and cache-content verification
     * during development and production debugging activities.
     *
     * @param action operation being performed
     * @param key cache key involved in the operation
     * @param value value returned or produced by the operation
     */
    protected void logItem(String action, Object key, Object value){

        if (!logger().isTraceEnabled()) {
            return;
        }
        logger().trace("[{}] {} key ={},value={}", cacheName(), action, key, value);

    }


}
