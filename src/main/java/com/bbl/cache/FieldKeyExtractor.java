package com.bbl.cache;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * A {@link KeyExtractor} that reads a named field off any object via reflection, instead of
 * requiring a hand-written lambda. Resolution order per object, for field name {@code "foo"}:
 * <ol>
 *   <li>a no-arg method named exactly {@code foo} (matches record components, e.g. {@code id()})</li>
 *   <li>a no-arg method named {@code getFoo} (standard JavaBean getter)</li>
 *   <li>a no-arg method named {@code isFoo} (boolean JavaBean getter)</li>
 *   <li>a declared field named {@code foo}, walking up the superclass chain (works even without
 *       a public getter)</li>
 * </ol>
 * The resolved value's {@code toString()} becomes the cache key. Register via
 * {@link CacheBuilder#withKeyField(String)} rather than constructing this directly.
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
        Object rawKey = readByAccessorMethod(value).orElseGet(() -> readByField(value));
        if (rawKey == null) {
            throw new CacheConfigurationException(
                    "Field '" + fieldName + "' resolved to null on " + value.getClass().getName());
        }
        return rawKey.toString();
    }

    private java.util.Optional<Object> readByAccessorMethod(T value) {
        String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String candidate : List.of(fieldName, "get" + capitalized, "is" + capitalized)) {
            try {
                Method method = value.getClass().getMethod(candidate);
                method.setAccessible(true);
                return java.util.Optional.ofNullable(invoke(method, value));
            } catch (NoSuchMethodException ignored) {
                // try next candidate, then fall back to field access
            }
        }
        return java.util.Optional.empty();
    }

    private Object invoke(Method method, T value) {
        try {
            return method.invoke(value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new CacheConfigurationException(
                    "Failed to read field '" + fieldName + "' via " + method + " on " + value.getClass().getName(), e);
        }
    }

    private Object readByField(T value) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(value);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the superclass chain
            } catch (IllegalAccessException e) {
                throw new CacheConfigurationException(
                        "Failed to read field '" + fieldName + "' on " + value.getClass().getName(), e);
            }
        }
        throw new CacheConfigurationException(
                "No field or accessor named '" + fieldName + "' found on " + value.getClass().getName());
    }
}
