package com.bbl.cache;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link KeyExtractor} that reads a named field off any object via reflection, instead of
 * requiring a hand-written lambda. For a given object, {@code fieldName} is resolved by:
 * <ol>
 *   <li>a no-arg method named exactly {@code fieldName} (matches record components, e.g. {@code id()})</li>
 *   <li>a no-arg method named {@code getFieldName} (standard JavaBean getter)</li>
 *   <li>a no-arg method named {@code isFieldName} (boolean JavaBean getter)</li>
 *   <li>a declared field named {@code fieldName}, walking up the superclass chain</li>
 * </ol>
 * If none of those resolve directly, the object's own non-simple fields (e.g. a JPA
 * {@code @EmbeddedId}-style composite key object) are searched the same way, recursively — so
 * {@code withKeyField("referenceId")} finds {@code referenceId} whether it's a direct field or
 * nested inside an embedded key object. The resolved value's {@code toString()} becomes the cache
 * key. Register via {@link CacheBuilder#withKeyField(String)} rather than constructing this
 * directly.
 */
final class FieldKeyExtractor<T> implements KeyExtractor<T> {

    private final String fieldName;

    private FieldKeyExtractor(String fieldName) {
        this.fieldName = fieldName;
    }

    static <T> FieldKeyExtractor<T> of(String fieldName) {
        return new FieldKeyExtractor<>(Objects.requireNonNull(fieldName, "fieldName"));
    }

    @Override
    public String extractKey(T value) {
        Objects.requireNonNull(value, "value");
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Object rawKey = resolve(value, visited)
                .orElseThrow(() -> new CacheConfigurationException(
                        "No field or accessor named '" + fieldName + "' found on " + value.getClass().getName()
                                + " (including embedded/nested fields)"));
        if (rawKey == null) {
            throw new CacheConfigurationException(
                    "Field '" + fieldName + "' resolved to null on " + value.getClass().getName());
        }
        return rawKey.toString();
    }

    private Optional<Object> resolve(Object target, Set<Object> visited) {
        if (target == null || !visited.add(target)) {
            return Optional.empty();
        }
        Optional<Object> direct = readDirect(target);
        if (direct.isPresent()) {
            return direct;
        }
        for (Field field : declaredFields(target.getClass())) {
            if (isSimpleType(field.getType())) {
                continue;
            }
            Optional<Object> found = resolve(readField(target, field), visited);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<Object> readDirect(Object target) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String candidate : List.of(fieldName, "get" + capitalized, "is" + capitalized)) {
            try {
                Method method = target.getClass().getMethod(candidate);
                method.setAccessible(true);
                return Optional.ofNullable(invoke(method, target));
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            }
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                return Optional.ofNullable(readField(target, field));
            } catch (NoSuchFieldException ignored) {
                // keep walking up the superclass chain
            }
        }
        return Optional.empty();
    }

    private Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new CacheConfigurationException(
                    "Failed to read field '" + fieldName + "' via " + method + " on " + target.getClass().getName(), e);
        }
    }

    private Object readField(Object target, Field field) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new CacheConfigurationException(
                    "Failed to read field '" + field.getName() + "' on " + target.getClass().getName(), e);
        }
    }

    private static List<Field> declaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Collections.addAll(fields, current.getDeclaredFields());
        }
        return fields;
    }

    /** Types not worth descending into when searching for an embedded key field. */
    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type.getName().startsWith("java.")
                || type.getName().startsWith("javax.")
                || type.getName().startsWith("jakarta.");
    }
}
