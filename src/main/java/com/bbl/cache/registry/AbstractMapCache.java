package com.bbl.cache.registry;

import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public sealed abstract class AbstractMapCache<K, S> implements KeyedCache<K, S>
        permits UniqueCache, GroupedCache, DoubleKeyCache {

    private final String name;
    private final Logger logger;

    protected volatile Map<K, S> storedCache = Map.of();

    protected AbstractMapCache(String name, Logger logger) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

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


    protected final Logger logger() {
        return logger;
    }


    protected final String cacheName() {
        return name;
    }

    /**
     * Atomic single-volatile-write swap of the whole snapshot. Cannot throw.
     *
     * <p>Deliberately not {@code final}: each sealed subclass overrides this
     * with a public, same-erasure {@code publish(...)} that calls
     * {@code super.publish(...)} then adds its trace logging. Because the
     * subclass's type parameter {@code S} is bound to a concrete type
     * ({@code V}, {@code List<V>}, or {@code Map<K2,V>}), the substituted
     * signature is identical to this method's, so a {@code final} modifier
     * here would make that override illegal. The {@code sealed permits}
     * clause on this class already closes the hierarchy to exactly the three
     * intended shapes, so {@code final} would add no real safety.
     */
    protected void publish(Map<K, S> snapshot) {
        this.storedCache = snapshot;
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
