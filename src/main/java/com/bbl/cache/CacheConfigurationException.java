package com.bbl.cache;

/** Thrown by {@link CacheBuilder} when a cache is assembled with missing/invalid configuration. */
public class CacheConfigurationException extends RuntimeException {

    public CacheConfigurationException(String message) {
        super(message);
    }

    /** @param cause typically a reflective access failure from {@link FieldKeyExtractor} */
    public CacheConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
