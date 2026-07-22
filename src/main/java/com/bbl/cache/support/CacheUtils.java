package com.bbl.cache.support;

public class CacheUtils {

    private CacheUtils(){}

    public static String buildKeys(String... key) {
        return String.join("_", key);
    }
}
