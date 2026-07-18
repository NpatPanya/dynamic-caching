package com.bbl.cache.registry;

/** Thrown on name collisions or type mismatches within a {@link CacheRegistry}. */
public class CacheRegistryException extends RuntimeException {
    public CacheRegistryException(String message) {
        super(message);
    }
}
