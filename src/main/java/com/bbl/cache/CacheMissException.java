package com.bbl.cache;

/** Thrown by {@link Cache#getOrThrow(String)} when no value is cached for the given key. */
public class CacheMissException extends RuntimeException {
    public CacheMissException(String message) {
        super(message);
    }
}
