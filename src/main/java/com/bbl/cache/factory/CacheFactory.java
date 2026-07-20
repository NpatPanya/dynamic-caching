package com.bbl.cache.factory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CacheFactory {

    private CacheFactory() {
    }

    public static <K, V> Map<K, V> from(
            Collection<V> values,
            Function<V, K> keyExtractor) {

        return values.stream()
                .collect(Collectors.toUnmodifiableMap(
                        keyExtractor,
                        Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate cache key");
                        }
                ));
    }


    public static <T, K, V> Map<K, V> from(
            Collection<T> values,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        return values.stream()
                .collect(Collectors.toUnmodifiableMap(
                        keyExtractor,
                        valueExtractor,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate cache key");
                        }
                ));
    }


    public static <T, K> Map<K, List<T>> groupedFrom(
            Collection<T> values,
            Function<T, K> keyExtractor) {

        return values.stream()
                .collect(Collectors.groupingBy(
                        keyExtractor,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                List::copyOf
                        )
                ));
    }

    public static <T, K, V> Map<K, List<V>> groupedFrom(
            Collection<T> values,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        return values.stream()
                .collect(Collectors.groupingBy(
                        keyExtractor,
                        Collectors.mapping(
                                valueExtractor,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        List::copyOf
                                )
                        )
                ));
    }


}
