package com.bbl.cache.support;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * Stateless transformations that filter collections into immutable lists,
 * unique maps, nested maps, groups, sorted views, and mapped views.
 *
 * <p>This class shapes data only. Registration, lifecycle, and TTL belong to
 * {@code CacheRegistry}.
 */
public final class DataFilter {

    private DataFilter() {
    }




    /** Filters a source collection into an immutable encounter-ordered list. */
    public static <T> List<T> filterToList(
            Collection<T> source, Predicate<? super T> predicate) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        List<T> result = new ArrayList<>();
        for (T element : source) {
            if (predicate.test(element)) {
                result.add(requireExtracted(element, "source element"));
            }
        }
        return List.copyOf(result);
    }

    public static <K, T> List<T> filterMapToList(
            Map<K, T> source,
            Predicate<T> predicate) {

        Objects.requireNonNull(source, "source must not be null");

        return filterToList(
                source.values(),
                predicate);
    }

    public static <K, T, V> List<V> filterMapToListExtractedValue(
            Map<K, T> source,
            Predicate<? super T> predicate,
            Function<? super T, V> valueExtractor) {

        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");

        return source.values()
                .stream()
                .filter(predicate)
                .map(valueExtractor)
                .map(v -> requireExtracted(v, "valueExtractor"))
                .toList();
    }


    public static <K1, K2, T> List<T> filterNestedMapToList(
            Map<K1, Map<K2, T>> source,
            Predicate<? super T> predicate) {

        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");

        return source.values()
                .stream()
                .flatMap(innerMap -> innerMap.values().stream())
                .filter(predicate)
                .map(value -> requireExtracted(value, "nested map value"))
                .toList();
    }

    public static <K1, K2, T, V> List<V> filterToListExtractedValue(
            Map<K1, Map<K2, T>> source,
            Predicate<? super T> predicate,
            Function<? super T, V> valueExtractor) {

        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");

        return source.values()
                .stream()
                .flatMap(m -> m.values().stream())
                .filter(predicate)
                .map(valueExtractor)
                .map(v -> requireExtracted(v, "valueExtractor"))
                .toList();
    }


    /** Filters and indexes source objects by a unique extracted key. */
    public static <T, K> Map<K, T> filterToMap(
            Collection<T> source,
            Predicate<? super T> predicate,
            Function<? super T, ? extends K> keyExtractor) {
        return filterToMap(source, predicate, keyExtractor, Function.identity());
    }

    /** Filters and indexes extracted values by a unique extracted key. */
    public static <T, K, V> Map<K, V> filterToMap(
            Collection<T> source,
            Predicate<? super T> predicate,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super T, ? extends V> valueExtractor) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");

        Map<K, V> result = new LinkedHashMap<>();
        for (T element : source) {
            if (!predicate.test(element)) {
                continue;
            }
            K key = requireExtracted(keyExtractor.apply(element), "keyExtractor");
            V value = requireExtracted(valueExtractor.apply(element), "valueExtractor");
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalStateException("Duplicate filtered key: " + key);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Filters and indexes source objects into an immutable two-level map. */
    public static <T, K1, K2> Map<K1, Map<K2, T>> filterToNestedMap(
            Collection<T> source,
            Predicate<? super T> predicate,
            Function<? super T, ? extends K1> outerKeyExtractor,
            Function<? super T, ? extends K2> innerKeyExtractor) {
        return filterToNestedMap(
                source, predicate, outerKeyExtractor, innerKeyExtractor, Function.identity());
    }

    /** Filters and indexes extracted values into an immutable two-level map. */
    public static <T, K1, K2, V> Map<K1, Map<K2, V>> filterToNestedMap(
            Collection<T> source,
            Predicate<? super T> predicate,
            Function<? super T, ? extends K1> outerKeyExtractor,
            Function<? super T, ? extends K2> innerKeyExtractor,
            Function<? super T, ? extends V> valueExtractor) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(outerKeyExtractor, "outerKeyExtractor must not be null");
        Objects.requireNonNull(innerKeyExtractor, "innerKeyExtractor must not be null");
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");

        Map<K1, Map<K2, V>> mutable = new LinkedHashMap<>();
        for (T element : source) {
            if (!predicate.test(element)) {
                continue;
            }
            K1 outerKey = requireExtracted(
                    outerKeyExtractor.apply(element), "outerKeyExtractor");
            K2 innerKey = requireExtracted(
                    innerKeyExtractor.apply(element), "innerKeyExtractor");
            V value = requireExtracted(valueExtractor.apply(element), "valueExtractor");
            Map<K2, V> inner = mutable.computeIfAbsent(outerKey, ignored -> new LinkedHashMap<>());
            if (inner.putIfAbsent(innerKey, value) != null) {
                throw new IllegalStateException(
                        "Duplicate filtered key pair: " + outerKey + " / " + innerKey);
            }
        }

        Map<K1, Map<K2, V>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, inner) ->
                immutable.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(inner))));
        return Collections.unmodifiableMap(immutable);
    }

    private static <T> T requireExtracted(T value, String extractorName) {
        if (value == null) {
            throw new IllegalArgumentException(extractorName + " must not return null");
        }
        return value;
    }



}