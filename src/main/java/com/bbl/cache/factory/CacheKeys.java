package com.bbl.cache.factory;

public class CacheKeys {

    private CacheKeys(){}

    public static String buildKeys(String... key) {
        return String.join("_", key);
    }
}
