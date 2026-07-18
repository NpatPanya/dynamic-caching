package com.bbl.cache;

/**
 * User-supplied mapping from a cached value to its String cache key — e.g. {@code User::getId}.
 * The library never derives keys via reflection or naming conventions on its own; you always
 * provide this explicitly, either as a lambda/method reference here, or by name via
 * {@link CacheBuilder#withKeyField(String)}.
 */
@FunctionalInterface
public interface KeyExtractor<T> {

    /**
     * Derives the cache key for {@code value}.
     *
     * @return a non-null String key; behavior is undefined if the same logical value produces
     *         different keys across calls
     */
    String extractKey(T value);
}
