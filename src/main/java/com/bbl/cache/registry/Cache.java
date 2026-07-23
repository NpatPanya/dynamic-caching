package com.bbl.cache.registry;

import com.bbl.cache.registry.deprecated.KeyedCache;

import java.util.Map;

/**
 * Read-only, shape-agnostic cache contract.
 *
 * <p>Implementations are immutable snapshots: there is no per-entry write
 * path on this interface. New code builds a snapshot via {@link Caches} and
 * registers it with {@link CacheRegistry}; reload is an atomic
 * registry-level replace ({@link CacheRegistry#reregister}), never an
 * in-place mutation of an existing {@code Cache}.
 *
 * <p>The legacy mutable methods ({@code computeIfAbsent}, {@code put},
 * {@code invalidate}, {@code invalidateAll}) live on {@link KeyedCache},
 * the deprecated inheritance-era sub-interface. New code never uses
 * {@code KeyedCache}.
 *
 * @deprecated Register raw data with {@link RegistryKey} and {@link CacheRegistry} instead.
 */
@Deprecated
public interface Cache<K, V> {
    V get(K key);

    V getOrDefault(K key, V defaultValue);

    boolean containsKey(K key);

    int size();

    boolean isEmpty();

    /** Immutable snapshot view of the cache contents. */
    Map<K, V> asMap();
}
