package com.bbl.cache.factory;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

abstract class AbstractMapCache<K, S> implements KeyedCache<K, S> {

    protected volatile Map<K, S> storedCache = Map.of();

    @Override public S get(K key)                 { return storedCache.get(key); }
    @Override public S getOrDefault(K key, S def) { return storedCache.getOrDefault(key, def); }
    @Override public boolean containsKey(K key)   { return storedCache.containsKey(key); }
    @Override public int size()                   { return storedCache.size(); }
    @Override public boolean isEmpty()            { return storedCache.isEmpty(); }
    @Override public Map<K, S> asMap()            { return storedCache; }
    @Override public void clear()                 { this.storedCache = Map.of(); }

    protected static void validate(Collection<?> collection, Function<?, ?> keyExtractor) {
        Objects.requireNonNull(collection, "collection must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
    }
}
