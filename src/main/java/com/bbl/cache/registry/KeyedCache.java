package com.bbl.cache.registry;

import java.util.Map;

/**
 * A generic key/value read view over an immutable snapshot.
 * Populated via subclasses of BasicCache and GroupedCache.
 * Distinct from the String-keyed public {@code com.bbl.cache.Cache}.
 */
public interface KeyedCache<K, V> {

    V get(K key);

    V getOrDefault(K key, V defaultValue);

    boolean containsKey(K key);

    void clear();

    int size();

    boolean isEmpty();

    Map<K, V> asMap();
}
