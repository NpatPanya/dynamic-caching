package com.bbl.cache.registry;

public interface CacheLoader<T> {

    T load();

    T get();

    String getName();

}
