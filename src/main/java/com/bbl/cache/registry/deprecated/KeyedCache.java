package com.bbl.cache.registry.deprecated;

import com.bbl.cache.registry.Cache;

import java.util.function.Function;

/**
 * Legacy internal mutable cache contract; new code uses {@link Cache}.
 *
 * <p>Implemented only by {@link AbstractMapCache} and the deprecated
 * inheritance-based shapes ({@link UniqueCache}, {@link GroupedCache},
 * {@link DoubleKeyCache}). It carries the per-entry write path
 * ({@code computeIfAbsent}/{@code put}/{@code invalidate}/
 * {@code invalidateAll}) that the read-only {@link Cache} contract no longer
 * exposes.
 */
@Deprecated
public interface KeyedCache<K, V> extends Cache<K, V> {

    V computeIfAbsent(K key, Function<? super K, ? extends V> loader);

    void put(K key, V value);

    void invalidate(K key);

    void invalidateAll();

    default void clear() {
        invalidateAll();
    }
}
