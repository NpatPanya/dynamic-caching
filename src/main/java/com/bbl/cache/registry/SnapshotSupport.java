package com.bbl.cache.registry;

import java.lang.reflect.Array;
import java.util.*;

/** Shared immutable-container snapshot support for registry entry points. */
final class SnapshotSupport {

    private SnapshotSupport() {
    }

    static <T> T snapshot(T value) {
        Objects.requireNonNull(value, "registered data must not be null");
        return cast(freeze(value));
    }

    static boolean requiresDefensiveCopy(Object value) {
        return containsArray(value);
    }

    static <T> T expose(T value, boolean defensiveCopy) {
        return defensiveCopy ? cast(freeze(value)) : value;
    }

    private static boolean containsArray(Object value) {
        if (value.getClass().isArray()) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .anyMatch(entry -> containsArray(entry.getKey())
                            || containsArray(entry.getValue()));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(SnapshotSupport::containsArray);
        }
        return false;
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) ->
                    copy.put(
                            freeze(Objects.requireNonNull(key, "map key must not be null")),
                            freeze(Objects.requireNonNull(
                                    nestedValue, "map value must not be null"))));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element ->
                    copy.add(freeze(Objects.requireNonNull(
                            element, "list element must not be null"))));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(element ->
                    copy.add(freeze(Objects.requireNonNull(
                            element, "set element must not be null"))));
            return Collections.unmodifiableSet(copy);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(element ->
                    copy.add(freeze(Objects.requireNonNull(
                            element, "collection element must not be null"))));
            return Collections.unmodifiableCollection(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                Array.set(copy, i, element == null ? null : freeze(element));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
