package com.bbl.cache.registry;

/** Thrown on name collisions or type mismatches within a {@link CacheRegistry}. */
public class CacheRegistryException extends RuntimeException {

    /** @param message should identify the offending name and, for mismatches, the expected vs. actual type */
    public CacheRegistryException(String message) {
        super(message);
    }
}
