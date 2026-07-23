package com.bbl.cache.registry;

import com.bbl.cache.support.CacheFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The v3 factory for building registrable, read-only {@link Cache}s directly
 * from data -- no subclassing required.
 *
 * <p>This is the single public entry point new code uses to build a
 * {@code Cache<K,V>}. It delegates the raw-map transforms to
 * {@link CacheFactory} (preserving its duplicate-key
 * {@link IllegalStateException} semantics unchanged), then wraps the
 * resulting immutable map in a read-only {@link ImmutableMapCache}.
 *
 * <p>Not to be confused with {@link com.bbl.cache.support.ViewFactory}, which
 * produces raw derived collections ({@code List}/{@code Map} views -- filter,
 * sort, map, group, unique) over already-materialized data, with no
 * {@code Cache} wrapper and no registration. Compose the two: filter with
 * {@code ViewFactory}, then index the result with {@code Caches}.
 */
public final class Caches {

    private Caches() {
    }

    public static <T, K> Cache<K, T> fromList(List<T> values, Function<T, K> keyExtractor) {
        return new ImmutableMapCache<>(CacheFactory.uniqueCache(values, keyExtractor));
    }

    public static <T, K, V> Cache<K, V> fromList(
            List<T> values, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        return new ImmutableMapCache<>(CacheFactory.uniqueCache(values, keyExtractor, valueExtractor));
    }

    /** Wraps already-keyed data directly; the source map is defensively copied, no re-keying. */
    public static <K, V> Cache<K, V> fromMap(Map<K, V> source) {
        return new ImmutableMapCache<>(source);
    }

    public static <T, K> Cache<K, List<T>> groupedFromList(List<T> values, Function<T, K> keyExtractor) {
        return new ImmutableMapCache<>(CacheFactory.groupCache(values, keyExtractor));
    }

    public static <T, K, V> Cache<K, List<V>> groupedFromList(
            List<T> values, Function<T, K> keyExtractor, Function<T, V> valueExtractor) {
        return new ImmutableMapCache<>(CacheFactory.groupCache(values, keyExtractor, valueExtractor));
    }

    public static <T, K1, K2> Cache<K1, Map<K2, T>> doubleKeyedFromList(
            List<T> values, Function<T, K1> k1, Function<T, K2> k2) {
        return new ImmutableMapCache<>(CacheFactory.doubleKeysCache(values, k1, k2));
    }

    public static <T, K1, K2, V> Cache<K1, Map<K2, V>> doubleKeyedFromList(
            List<T> values, Function<T, K1> k1, Function<T, K2> k2, Function<T, V> valueExtractor) {
        return new ImmutableMapCache<>(CacheFactory.doubleKeysCache(values, k1, k2, valueExtractor));
    }
}
