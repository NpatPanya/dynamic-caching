package com.bbl.cache.registry;

import com.bbl.cache.Cache;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional named holder for multiple independently-typed {@link Cache} instances, for
 * applications that bootstrap several caches at startup and look them up dynamically by name.
 * A single cache used at one call site does not need this — hold the {@link Cache} directly.
 */
public final class CacheRegistry {

    private final Map<String, Entry<?>> caches = new ConcurrentHashMap<>();

    private CacheRegistry() {
    }

    public static CacheRegistry create() {
        return new CacheRegistry();
    }

    public <V> void register(String name, Cache<V> cache, Class<V> valueType) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(valueType, "valueType");
        Entry<?> existing = caches.putIfAbsent(name, new Entry<>(cache, valueType));
        if (existing != null) {
            throw new CacheRegistryException("Cache already registered under name: " + name);
        }
    }

    @SuppressWarnings("unchecked")
    public <V> Cache<V> get(String name, Class<V> valueType) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(valueType, "valueType");
        Entry<?> entry = caches.get(name);
        if (entry == null) {
            throw new CacheRegistryException("No cache registered under name: " + name);
        }
        if (!entry.valueType.equals(valueType)) {
            throw new CacheRegistryException(
                    "Cache '" + name + "' was registered with value type " + entry.valueType.getName()
                            + " but requested as " + valueType.getName());
        }
        return (Cache<V>) entry.cache;
    }

    public boolean contains(String name) {
        return caches.containsKey(name);
    }

    public void clear() {
        caches.clear();
    }

    private record Entry<V>(Cache<V> cache, Class<V> valueType) {
    }
}
