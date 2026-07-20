package com.bbl.cache.factory;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

public abstract class BasicCache<K, V> extends AbstractMapCache<K, V> {

    protected void load(Collection<V> collection, Function<V, K> keyExtractor) {
        validate(collection, keyExtractor);
        this.storedCache = CacheFactory.from(collection, keyExtractor);
    }

    protected <T> void load(Collection<T> collection,
                            Function<T, K> keyExtractor,
                            Function<T, V> valueExtractor) {
        validate(collection, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        this.storedCache = CacheFactory.from(collection, keyExtractor, valueExtractor);
    }
}
