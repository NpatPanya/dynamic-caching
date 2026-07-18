package com.bbl.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A String-keyed cache of values of type {@code V}. Instances are created via {@link CacheBuilder}.
 */
public interface Cache<V> {

    Optional<V> get(String key);

    V getOrThrow(String key);

    boolean containsKey(String key);

    void put(String key, V value);

    void putAll(Collection<? extends V> values, KeyExtractor<? super V> keyExtractor);

    V remove(String key);

    void clear();

    int size();

    boolean isEmpty();

    Collection<V> values();

    /** Unmodifiable live view of the underlying store. */
    Map<String, V> asMap();

    void load(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor);

    /** Clears the cache, then repopulates it from {@code loader}. */
    void reload(CacheLoader<V> loader, KeyExtractor<? super V> keyExtractor);
}
