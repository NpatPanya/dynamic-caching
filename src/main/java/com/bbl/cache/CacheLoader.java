package com.bbl.cache;

import java.util.Collection;

/**
 * Supplies the values used to (re)populate a cache. Implemented by the consuming application —
 * e.g. wrapping a repository query, a file read, or a REST call. The library calls this exactly
 * when {@link Cache#load} or {@link Cache#reload} is invoked, which can be at startup or any
 * later point once the data is available — see {@code com.bbl.cache.example.ExampleUsage} in the
 * test sources for a worked "fetch now, cache later" example.
 */
@FunctionalInterface
public interface CacheLoader<V> {

    /** Returns the values to populate the cache with. Called synchronously, once per invocation. */
    Collection<V> load();
}
