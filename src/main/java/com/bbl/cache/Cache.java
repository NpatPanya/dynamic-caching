package com.bbl.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A String-keyed cache of values of type {@code V}. Instances are created via {@link CacheBuilder}
 * — see its class-level example, or {@code com.bbl.cache.example.ExampleUsage} in the test
 * sources for narrated end-to-end scenarios (plain objects, DTOs, JPA entities, list values,
 * embedded keys, the registry, etc.).
 *
 * <p>Backed by a thread-safe implementation ({@code ConcurrentHashMap}) — safe to read while
 * another thread populates or reloads it, though a reload may transiently reduce visible entries
 * (see {@link #reload}).
 */
public interface Cache<V> {

    /** Returns the value cached under {@code key}, or {@link Optional#empty()} if absent. */
    Optional<V> get(String key);

    /**
     * Returns the value cached under {@code key}.
     *
     * @throws CacheMissException if no value is cached under {@code key}
     */
    V getOrThrow(String key);

    /** Returns whether a value is cached under {@code key}. */
    boolean containsKey(String key);

    /** Caches {@code value} under {@code key}, replacing any existing value for that key. */
    void put(String key, V value);

    /**
     * Caches every element of {@code values}, deriving each one's key via {@code keyExtractor}.
     * If two elements extract to the same key, the later one (in iteration order) wins.
     */
    void putAll(Collection<? extends V> values, KeyExtractor<? super V> keyExtractor);

    /** Removes and returns the value cached under {@code key}, or {@code null} if absent. */
    V remove(String key);

    /** Removes every entry from the cache. */
    void clear();

    /** Returns the number of entries currently cached. */
    int size();

    /** Returns whether the cache currently has no entries. */
    boolean isEmpty();

    /** Returns a view of all currently cached values, in no particular order. */
    Collection<V> values();

    /** Unmodifiable live view of the underlying store — reflects concurrent writes, but rejects mutation. */
    Map<String, V> asMap();

    /**
     * Populates the cache from {@code loader}, keying each loaded element via {@code keyExtractor}.
     * Existing entries are kept (new/overlapping keys are overwritten); use {@link #reload} to
     * clear first. Synchronous — call this from whatever startup hook your framework provides
     * (a {@code @PostConstruct} method, {@code main()}, a servlet listener, etc.), or at any later
     * point once you already have data to load, e.g. right after a repository call returns.
     */
    void load(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor);

    /**
     * Clears the cache, then repopulates it from {@code loader} via {@code keyExtractor}. This is
     * a simple clear-then-load — readers may transiently see fewer entries (or none) while a
     * reload is in progress; there is no atomic swap in v1.
     */
    void reload(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor);
}
