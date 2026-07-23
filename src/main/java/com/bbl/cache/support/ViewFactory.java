package com.bbl.cache.support;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;




public final class ViewFactory {

    private ViewFactory() {
    }

    /*
     * =============================
     * LIST VIEW
     * =============================
     */

    public static <T> List<T> listView(
            Collection<T> source) {

        Objects.requireNonNull(source);

        return List.copyOf(source);
    }

    public static <K, T> List<T> listViewOfMap(
            Map<K, T> source) {

        Objects.requireNonNull(source);

        return List.copyOf(source.values());
    }

    public static <K1, K2, T> List<T> listViewOfDoubleKey(
            Map<K1, Map<K2, T>> source) {

        Objects.requireNonNull(source);

        return source.values()
                .stream()
                .flatMap(m -> m.values().stream())
                .toList();
    }

    /*
     * =============================
     * GROUP VIEW
     * =============================
     */

    public static <T, G> Map<G, List<T>> groupedView(
            Collection<T> source,
            Function<T, G> groupingFn) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(groupingFn);

        return source.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                groupingFn,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        List::copyOf)),
                        Collections::unmodifiableMap));
    }

    public static <K, T, G> Map<G, List<T>> groupedViewOfMap(
            Map<K, T> source,
            Function<T, G> groupingFn) {

        return groupedView(
                source.values(),
                groupingFn);
    }

    public static <K1, K2, T, G> Map<G, List<T>> groupedViewOfDoubleKey(
            Map<K1, Map<K2, T>> source,
            Function<T, G> groupingFn) {

        return groupedView(
                listViewOfDoubleKey(source),
                groupingFn);
    }

    /*
     * =============================
     * FILTER VIEW
     * =============================
     */

    public static <T> List<T> filteredView(
            Collection<T> source,
            Predicate<T> predicate) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(predicate);

        return source.stream()
                .filter(predicate)
                .toList();
    }

    public static <K, T> List<T> filteredViewOfMap(
            Map<K, T> source,
            Predicate<T> predicate) {

        return filteredView(
                source.values(),
                predicate);
    }

    public static <K1, K2, T> List<T> filteredViewOfDoubleKey(
            Map<K1, Map<K2, T>> source,
            Predicate<T> predicate) {

        return filteredView(
                listViewOfDoubleKey(source),
                predicate);
    }

    /*
     * =============================
     * SORT VIEW
     * =============================
     */

    public static <T> List<T> sortedView(
            Collection<T> source,
            Comparator<T> comparator) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(comparator);

        return source.stream()
                .sorted(comparator)
                .toList();
    }

    public static <K, T> List<T> sortedViewOfMap(
            Map<K, T> source,
            Comparator<T> comparator) {

        return sortedView(
                source.values(),
                comparator);
    }

    public static <K1, K2, T> List<T> sortedViewOfDoubleKey(
            Map<K1, Map<K2, T>> source,
            Comparator<T> comparator) {

        return sortedView(
                listViewOfDoubleKey(source),
                comparator);
    }

    public static <T, K> Map<K, T> uniqueView(
            Collection<T> source,
            Function<T, K> keyFn) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(keyFn);

        return source.stream()
                .collect(Collectors.toUnmodifiableMap(
                        keyFn,
                        Function.identity(),
                        throwingMerger()));
    }

    public static <K, T, UK> Map<UK, T> uniqueViewOfMap(
            Map<K, T> source,
            Function<T, UK> keyFn) {

        return uniqueView(
                source.values(),
                keyFn);
    }

    public static <K1, K2, T, UK> Map<UK, T> uniqueViewOfDoubleKey(
            Map<K1, Map<K2, T>> source,
            Function<T, UK> keyFn) {

        return uniqueView(
                listViewOfDoubleKey(source),
                keyFn);
    }


    public static <T, R> List<R> mappedView(
            Collection<T> source,
            Function<T, R> mapper) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(mapper);

        return source.stream()
                .map(mapper)
                .toList();
    }

    public static <K, T, R> List<R> mappedViewOfMap(
            Map<K, T> source,
            Function<T, R> mapper) {

        return mappedView(
                source.values(),
                mapper);
    }

    public static <K1, K2, T, R> List<R> mappedViewFromDoubleKey(
            Map<K1, Map<K2, T>> source,
            Function<T, R> mapper) {

        return mappedView(
                listViewOfDoubleKey(source),
                mapper);
    }


    public static <T, G, V> Map<G, List<V>> groupedView(
            Collection<T> source,
            Function<T, G> groupingFn,
            Function<T, V> valueFn) {

        Objects.requireNonNull(source);
        Objects.requireNonNull(groupingFn);
        Objects.requireNonNull(valueFn);

        return source.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                groupingFn,
                                Collectors.mapping(
                                        valueFn,
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                List::copyOf))),
                        Collections::unmodifiableMap));
    }

    public static <K, T, G, V> Map<G, List<V>>
    groupedViewFromMap(
            Map<K, T> source,
            Function<T, G> groupingFn,
            Function<T, V> valueFn) {

        return groupedView(
                source.values(),
                groupingFn,
                valueFn);
    }

    public static <K1, K2, T, G, V> Map<G, List<V>>
    groupedViewFromDoubleKey(
            Map<K1, Map<K2, T>> source,
            Function<T, G> groupingFn,
            Function<T, V> valueFn) {

        return groupedView(
                listViewOfDoubleKey(source),
                groupingFn,
                valueFn);
    }


    private static <T> BinaryOperator<T> throwingMerger() {
        return (a, b) -> {
            throw new IllegalStateException("Duplicate view key");
        };
    }
}