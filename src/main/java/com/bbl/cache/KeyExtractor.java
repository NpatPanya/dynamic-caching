package com.bbl.cache;

/**
 * User-supplied mapping from a cached value to its String cache key. The library never derives
 * keys via reflection or naming conventions — callers always provide this explicitly.
 */
@FunctionalInterface
public interface KeyExtractor<T> {
    String extractKey(T value);
}
