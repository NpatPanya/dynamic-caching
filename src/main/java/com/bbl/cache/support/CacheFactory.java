package com.bbl.cache.support;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory class responsible for constructing immutable cache structures.
 *
 * <p>This class provides utility methods for transforming collections into
 * cache-friendly map structures used by the cache framework.
 *
 * <p>All generated cache structures are immutable and suitable for atomic
 * replacement within cache implementations.
 *
 * <p>Unless explicitly documented otherwise, duplicate keys are not allowed.
 * Cache creation fails with an {@link IllegalStateException} if duplicate
 * keys are encountered during construction.
 *
 * <p>Supported cache structures include:
 *
 * <ul>
 *     <li>{@code Map<K, V>}</li>
 *     <li>{@code Map<K, List<V>>}</li>
 *     <li>{@code Map<K1, Map<K2, V>>}</li>
 * </ul>
 *
 * <p>This class is not intended to be instantiated.
 */


public final class CacheFactory {

    private CacheFactory() {
    }

    /**
     * Creates an immutable one-to-one cache mapping.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K, V>
     * </pre>
     *
     * where the key is produced by {@code keyExtractor} and the original
     * collection element is stored as the value.
     *
     * <p>Duplicate keys are not permitted.
     *
     * @param values source collection
     * @param keyExtractor function that derives the cache key
     * @param <K> cache key type
     * @param <V> cache value type
     * @return immutable cache map
     * @throws IllegalStateException if duplicate keys are encountered
     */
    public static <K, V> Map<K, V> uniqueCache(
            Collection<V> values,
            Function<V, K> keyExtractor) {

        return values.stream()
                .collect(Collectors.toUnmodifiableMap(
                        keyExtractor,
                        Function.identity(),
                        throwingMerger()
                ));
    }


    /**
     * Creates an immutable one-to-one cache mapping using independent
     * key and value extractors.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K, V>
     * </pre>
     *
     * <p>This overload is useful when the source object and cache value
     * are different types.
     *
     * <p>Duplicate keys are not permitted.
     *
     * @param values source collection
     * @param keyExtractor function that derives the cache key
     * @param valueExtractor function that derives the cache value
     * @param <T> source element type
     * @param <K> cache key type
     * @param <V> cache value type
     * @return immutable cache map
     * @throws IllegalStateException if duplicate keys are encountered
     */
    public static <T, K, V> Map<K, V> uniqueCache(
            Collection<T> values,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        return values.stream()
                .collect(Collectors.toUnmodifiableMap(
                        keyExtractor,
                        valueExtractor,
                        throwingMerger()
                ));
    }


    /**
     * Creates an immutable grouped cache structure.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K, List<T>>
     * </pre>
     *
     * where all elements producing the same key are grouped together.
     *
     * <p>The returned map and all contained lists are immutable.
     *
     * <p>This method is suitable for one-to-many relationships where a
     * cache key may contain multiple associated values.
     *
     * @param values source collection
     * @param keyExtractor function that derives the grouping key
     * @param <T> source element type
     * @param <K> grouping key type
     * @return immutable grouped cache
     */
    public static <T, K> Map<K, List<T>> groupCache(
            Collection<T> values,
            Function<T, K> keyExtractor) {

        return values.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                keyExtractor,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        List::copyOf
                                )
                        ),
                        Collections::unmodifiableMap
                ));
    }

    /**
     * Creates an immutable grouped cache structure using a custom value
     * extraction function.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K, List<V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *     <li>{@code keyExtractor} determines the grouping key</li>
     *     <li>{@code valueExtractor} determines the cached value</li>
     * </ul>
     *
     * <p>The returned map and all contained lists are immutable.
     *
     * @param values source collection
     * @param keyExtractor function that derives the grouping key
     * @param valueExtractor function that derives the cached value
     * @param <T> source element type
     * @param <K> grouping key type
     * @param <V> cached value type
     * @return immutable grouped cache
     */
    public static <T, K, V> Map<K, List<V>> groupCache(
            Collection<T> values,
            Function<T, K> keyExtractor,
            Function<T, V> valueExtractor) {

        return values.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                keyExtractor,
                                Collectors.mapping(
                                        valueExtractor,
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                List::copyOf
                                        )
                                )
                        ),
                        Collections::unmodifiableMap
                ));
    }

    /**
     * Creates an immutable two-level grouped cache structure.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K1, Map<K2, T>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *     <li>{@code key1Extractor} produces the outer grouping key</li>
     *     <li>{@code key2Extractor} produces the inner unique key</li>
     *     <li>the original object is stored as the cached value</li>
     * </ul>
     *
     * <p>This structure is intended for data that is naturally grouped by
     * a primary key while remaining uniquely identifiable by a secondary
     * key within each group.
     *
     * <p>Example:
     *
     * <pre>
     * serviceName
     *     -> providerId
     *         -> ServiceProviderProfile
     * </pre>
     *
     * <pre>
     * serviceName + clientId
     *     -> providerId + providerResponseCode
     *         -> ResponseMapping
     * </pre>
     *
     * <p>Duplicate combinations of {@code K1} and {@code K2} are not
     * permitted. Cache creation fails if more than one entry resolves to
     * the same key pair.
     *
     * <p>The returned outer map and all nested maps are immutable.
     *
     * @param values source collection
     * @param key1Extractor function that derives the outer grouping key
     * @param key2Extractor function that derives the inner unique key
     * @param <T> source element type
     * @param <K1> outer grouping key type
     * @param <K2> inner unique key type
     * @return immutable two-level grouped cache
     * @throws IllegalStateException if duplicate key pairs are encountered
     */
    public static <T, K1, K2> Map<K1, Map<K2, T>> doubleKeysCache(
            Collection<T> values,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor) {

        return values.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                key1Extractor,
                                Collectors.collectingAndThen(
                                        Collectors.toMap(
                                                key2Extractor,
                                                Function.identity(),
                                                throwingMerger()
                                        ),
                                        Collections::unmodifiableMap
                                )
                        ),
                        Collections::unmodifiableMap
                ));
    }

    /**
     * Creates an immutable two-level grouped cache structure using
     * independent value extraction.
     *
     * <p>The supplied collection is transformed into:
     *
     * <pre>
     * Map<K1, Map<K2, V>>
     * </pre>
     *
     * where:
     *
     * <ul>
     *     <li>{@code key1Extractor} produces the outer grouping key</li>
     *     <li>{@code key2Extractor} produces the inner unique key</li>
     *     <li>{@code valueExtractor} produces the cached value</li>
     * </ul>
     *
     * <p>This overload is useful when the source object and cache value
     * are different types.
     *
     * <p>Example:
     *
     * <pre>
     * serviceName
     *     -> providerResponseCode
     *         -> systemResponseCode
     * </pre>
     *
     * <p>instead of:
     *
     * <pre>
     * serviceName
     *     -> providerResponseCode
     *         -> AnygwResponseMapping
     * </pre>
     *
     * <p>Duplicate combinations of {@code K1} and {@code K2} are not
     * permitted. Cache creation fails if more than one entry resolves to
     * the same key pair.
     *
     * <p>The returned outer map and all nested maps are immutable.
     *
     * @param values source collection
     * @param key1Extractor function that derives the outer grouping key
     * @param key2Extractor function that derives the inner unique key
     * @param valueExtractor function that derives the cached value
     * @param <T> source element type
     * @param <K1> outer grouping key type
     * @param <K2> inner unique key type
     * @param <V> cached value type
     * @return immutable two-level grouped cache
     * @throws IllegalStateException if duplicate key pairs are encountered
     */
    public static <T, K1, K2, V> Map<K1, Map<K2, V>> doubleKeysCache(
            Collection<T> values,
            Function<T, K1> key1Extractor,
            Function<T, K2> key2Extractor,
            Function<T, V> valueExtractor) {

        return values.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                key1Extractor,
                                Collectors.collectingAndThen(
                                        Collectors.toMap(
                                                key2Extractor,
                                                valueExtractor,
                                                throwingMerger()
                                        ),
                                        Collections::unmodifiableMap
                                )
                        ),
                        Collections::unmodifiableMap
                ));
    }

    /**
     * Creates a merge function that rejects duplicate keys during map
     * construction.
     *
     * <p>This method is used internally by cache factory methods to
     * guarantee key uniqueness and prevent accidental overwriting of
     * cached data.
     *
     * @param <T> cache value type
     * @return merge function that always throws
     * @throws IllegalStateException whenever a duplicate key is encountered
     */
    private static <T> BinaryOperator<T> throwingMerger() {
        return (a, b) -> {
            throw new IllegalStateException("Duplicate cache key");
        };
    }
}
