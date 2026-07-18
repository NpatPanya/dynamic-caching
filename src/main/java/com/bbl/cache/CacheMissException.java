package com.bbl.cache;

/** Thrown by {@link Cache#getOrThrow(String)} when no value is cached for the given key. */
public class CacheMissException extends RuntimeException {

    /** @param message should identify the missing key, e.g. {@code "No value cached for key: 42"} */
    public CacheMissException(String message) {
        super(message);
    }
}
