package com.bbl.cache.registry;

import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A registry that manages named cache components and provides
 * cross-cutting operations across multiple cache shapes.
 *
 * <p>CacheGroup enables a single bean to hold and coordinate multiple
 * cache shapes ({@link UniqueCache}, {@link GroupedCache}, {@link DoubleKeyCache})
 * without forcing inheritance. Each component is registered by name and
 * can be accessed for coordinated operations like {@link #clearAll()} or
 * {@link #totalSize()}.
 *
 * <p>Component names must be unique; attempting to register a duplicate
 * throws {@link IllegalStateException}.
 *
 * <p>This class is a plain POJO intended to be instantiated and held
 * by application beans, not injected via CDI.
 */
public final class CacheFacade {

    private final String groupName;
    private final Logger logger;
    private final Map<String, KeyedCache<?, ?>> components = new LinkedHashMap<>();

    /**
     * Constructs a new cache group with the given name and logger.
     *
     * @param groupName the name of this cache group (must not be null)
     * @param logger the logger for operational traces (must not be null)
     * @throws NullPointerException if groupName or logger is null
     */
    public CacheFacade(String groupName, Logger logger) {
        this.groupName = Objects.requireNonNull(groupName, "groupName");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Creates and registers a new {@link UniqueCache} component.
     *
     * @param name the component name (must be unique within this group)
     * @param <K> cache key type
     * @param <V> cache value type
     * @return the newly registered UniqueCache
     * @throws IllegalStateException if a component with this name is already registered
     * @throws NullPointerException if name is null
     */
    public <K, V> UniqueCache<K, V> uniqueCache(String name) {
        return reg(name, new UniqueCache<>(name, logger));
    }

    /**
     * Creates and registers a new {@link GroupedCache} component.
     *
     * @param name the component name (must be unique within this group)
     * @param <K> grouping key type
     * @param <V> cache value type
     * @return the newly registered GroupedCache
     * @throws IllegalStateException if a component with this name is already registered
     * @throws NullPointerException if name is null
     */
    public <K, V> GroupedCache<K, V> groupedCache(String name) {
        return reg(name, new GroupedCache<>(name, logger));
    }

    /**
     * Creates and registers a new {@link DoubleKeyCache} component.
     *
     * @param name the component name (must be unique within this group)
     * @param <K1> primary key type
     * @param <K2> secondary key type
     * @param <V> cache value type
     * @return the newly registered DoubleKeyCache
     * @throws IllegalStateException if a component with this name is already registered
     * @throws NullPointerException if name is null
     */
    public <K1, K2, V> DoubleKeyCache<K1, K2, V> doubleKeyCache(String name) {
        return reg(name, new DoubleKeyCache<>(name, logger));
    }

    /**
     * Registers a cache component under the given name.
     *
     * <p>This is an internal helper used by the factory methods.
     * It enforces uniqueness of component names.
     *
     * @param name the component name
     * @param c the cache component to register
     * @param <C> cache type
     * @return the registered component
     * @throws IllegalStateException if a component with this name is already registered
     */
    private <C extends KeyedCache<?, ?>> C reg(String name, C c) {
        if (components.putIfAbsent(name, c) != null) {
            throw new IllegalStateException("duplicate cache component name: " + name);
        }
        return c;
    }

    /**
     * Clears all registered cache components.
     *
     * <p>If trace logging is enabled, emits a trace line naming the group
     * and the number of cleared components.
     */
    public void clearAll() {
        components.values().forEach(KeyedCache::clear);
        if (logger.isTraceEnabled()) {
            logger.trace("[{}] cleared {} components", groupName, components.size());
        }
    }

    /**
     * Returns the total number of entries across all registered components.
     *
     * @return sum of {@code size()} for all components
     */
    public int totalSize() {
        return components.values().stream().mapToInt(KeyedCache::size).sum();
    }

    /**
     * Returns a map of component name to size for all registered components.
     *
     * <p>The returned map preserves insertion order.
     *
     * @return an immutable map of component names to their sizes
     */
    public Map<String, Integer> sizes() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, KeyedCache<?, ?>> entry : components.entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    /**
     * Returns an unmodifiable set of all registered component names.
     *
     * @return an unmodifiable set of component names
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(components.keySet());
    }

    /**
     * Retrieves a registered cache component by name.
     *
     * <p>This provides a read-only central view of registered components
     * and is primarily intended for cross-cutting operations like
     * {@link #clearAll()}, not for typed reads (which go through
     * the bean's typed field).
     *
     * @param name the component name
     * @return the component, or null if not registered
     */
    public KeyedCache<?, ?> get(String name) {
        return components.get(name);
    }
}
