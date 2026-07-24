package com.bbl.cache.registry;

public abstract class AbstractCacheLoader<T> implements CacheLoader<T> {

    @Override
    public abstract T load();

    @Override
    public abstract T get();

    @Override
    public abstract String getName();
}
