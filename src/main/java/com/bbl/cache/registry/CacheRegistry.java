package com.bbl.cache.registry;

import com.bbl.cache.Cache;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional named holder for multiple independently-typed {@link Cache} instances, for
 * applications that bootstrap several caches at startup and look them up dynamically by name.
 * A single cache used at one call site does not need this — hold the {@link Cache} directly.
 *
 * <p>{@code Cache<List<E>>} (a cache whose values are whole lists) is supported via
 * {@link #registerList} / {@link #getList}. A separate pair of methods is needed because
 * {@code Class<List<E>>} cannot be expressed directly under Java's type erasure — {@code
 * registerList}/{@code getList} take the list's element type instead and track "this entry is a
 * list of E" internally, so callers never need an unchecked cast.
 */
public final class CacheRegistry {

    private final Map<String, Entry> caches = new ConcurrentHashMap<>();

    private CacheRegistry() {
    }

    /** Creates an empty registry. */
    public static CacheRegistry create() {
        return new CacheRegistry();
    }

    /**
     * Registers {@code cache} under {@code name} for later retrieval via {@link #get}.
     *
     * @throws CacheRegistryException if {@code name} is already registered (under this or
     *                                 {@link #registerList})
     */
    public <V> void register(String name, Cache<V> cache, Class<V> valueType) {
        put(name, cache, valueType, false);
    }

    /**
     * Registers a cache whose values are whole lists, e.g. {@code Cache<List<UserDto>>}, for
     * later retrieval via {@link #getList}. {@code elementType} is the list's element type
     * ({@code UserDto.class}), not {@code List.class}.
     *
     * @throws CacheRegistryException if {@code name} is already registered (under this or
     *                                 {@link #register})
     */
    public <E> void registerList(String name, Cache<List<E>> cache, Class<E> elementType) {
        put(name, cache, elementType, true);
    }

    private void put(String name, Cache<?> cache, Class<?> typeToken, boolean isList) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(typeToken, "typeToken");
        Entry existing = caches.putIfAbsent(name, new Entry(cache, typeToken, isList));
        if (existing != null) {
            throw new CacheRegistryException("Cache already registered under name: " + name);
        }
    }

    /**
     * Retrieves a cache registered via {@link #register}.
     *
     * @throws CacheRegistryException if no cache is registered under {@code name}, or it was
     *                                 registered with a different value type or via {@link #registerList}
     */
    @SuppressWarnings("unchecked")
    public <V> Cache<V> get(String name, Class<V> valueType) {
        return (Cache<V>) lookup(name, valueType, false).cache();
    }

    /**
     * Retrieves a cache registered via {@link #registerList}.
     *
     * @throws CacheRegistryException if no cache is registered under {@code name}, or it was
     *                                 registered with a different element type or via {@link #register}
     */
    @SuppressWarnings("unchecked")
    public <E> Cache<List<E>> getList(String name, Class<E> elementType) {
        return (Cache<List<E>>) lookup(name, elementType, true).cache();
    }

    private Entry lookup(String name, Class<?> typeToken, boolean expectList) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeToken, "typeToken");
        Entry entry = caches.get(name);
        if (entry == null) {
            throw new CacheRegistryException("No cache registered under name: " + name);
        }
        if (entry.isList() != expectList || !entry.typeToken().equals(typeToken)) {
            throw new CacheRegistryException(
                    "Cache '" + name + "' was registered as " + describe(entry.typeToken(), entry.isList())
                            + " but requested as " + describe(typeToken, expectList));
        }
        return entry;
    }

    /** Returns whether any cache (scalar or list) is registered under {@code name}. */
    public boolean contains(String name) {
        return caches.containsKey(name);
    }

    /** Removes every registration. The underlying {@link Cache} instances themselves are untouched. */
    public void clear() {
        caches.clear();
    }

    private static String describe(Class<?> typeToken, boolean isList) {
        return isList ? "List<" + typeToken.getName() + ">" : typeToken.getName();
    }

    private record Entry(Cache<?> cache, Class<?> typeToken, boolean isList) {
    }
}
