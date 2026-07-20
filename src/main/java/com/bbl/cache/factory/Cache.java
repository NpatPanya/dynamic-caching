package com.bbl.cache.factory;

import java.util.Map;

public interface Cache<K, V> {

    V get(K key);

    V getOrDefault(K key, V defaultValue);

    boolean containsKey(K key);

    void clear();

    int size();

    boolean isEmpty();

    Map<K, V> asMap();

//    void load(Collection<V> collection, Function<V, K> keyExtractor);

//    <T> void load(Collection<T> collection, Function<T, K> keyExtractor, Function<T, V> valueExtractor);
}







