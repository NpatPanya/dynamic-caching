package com.bbl.cache;

import java.util.Collection;

/**
 * Supplies the values used to (re)populate a cache. Implemented by the consuming application —
 * e.g. wrapping a repository query, a file read, or a REST call made at startup.
 */
@FunctionalInterface
public interface CacheLoader<V> {
    Collection<V> load();
}
