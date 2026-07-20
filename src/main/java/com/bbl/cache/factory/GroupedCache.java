package com.bbl.cache.factory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public abstract class GroupedCache<K, V> extends AbstractMapCache<K, List<V>> {

    protected void load(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        this.storedCache = CacheFactory.groupedFrom(collection, keyExtractor);
    }

    protected <T> void load(Collection<T> collection,
                            Function<T, K> keyExtractor,
                            Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        this.storedCache = CacheFactory.groupedFrom(collection, keyExtractor, valueExtractor);
    }

    @Override
    public List<V> get(K key) {
        return storedCache.getOrDefault(key, List.of());
    }
}
