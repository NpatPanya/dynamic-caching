package com.bbl.cache.registry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Identity-based typed key for arbitrary data stored in {@link CacheRegistry}.
 *
 * <p>Declare keys as shared constants and use the same instance for
 * registration and retrieval. Identity semantics prevent another key with the
 * same name from claiming an incompatible generic type.
 *
 * @deprecated Prefer string-keyed {@link CacheRegistry} methods and strict assignment types.
 */
@Deprecated
public final class RegistryKey<T> {

    private final String name;
    private final Function<T, T> snapshotter;
    private final Function<T, T> exposer;

    private RegistryKey(String name, Function<T, T> snapshotter, Function<T, T> exposer) {
        this.name = validateName(name);
        this.snapshotter = snapshotter;
        this.exposer = exposer;
    }

    public static <T> RegistryKey<T> value(String name, Class<T> valueType) {
        Objects.requireNonNull(valueType, "valueType must not be null");
        if (Collection.class.isAssignableFrom(valueType) || Map.class.isAssignableFrom(valueType)) {
            throw new IllegalArgumentException(
                    "Use list(...), set(...), map(...), or nestedMap(...) for collection values");
        }
        Function<T, T> copier = value -> {
            Objects.requireNonNull(value, "registered data must not be null");
            if (!valueType.isInstance(value)) {
                throw typeMismatch(name, valueType, value);
            }
            return SnapshotSupport.snapshot(value);
        };
        return new RegistryKey<>(name, copier, copier);
    }

    public static <E> RegistryKey<List<E>> list(String name, Class<E> elementType) {
        Objects.requireNonNull(elementType, "elementType must not be null");
        Function<List<E>, List<E>> snapshotter = source -> {
            Objects.requireNonNull(source, "registered data must not be null");
            validateElements(name, source, elementType, "list element");
            return SnapshotSupport.snapshot(source);
        };
        return new RegistryKey<>(name, snapshotter, Function.identity());
    }

    public static <E> RegistryKey<Set<E>> set(String name, Class<E> elementType) {
        Objects.requireNonNull(elementType, "elementType must not be null");
        Function<Set<E>, Set<E>> snapshotter = source -> {
            Objects.requireNonNull(source, "registered data must not be null");
            validateElements(name, source, elementType, "set element");
            return SnapshotSupport.snapshot(source);
        };
        return new RegistryKey<>(name, snapshotter, Function.identity());
    }

    public static <K, V> RegistryKey<Map<K, V>> map(
            String name, Class<K> keyType, Class<V> valueType) {
        Objects.requireNonNull(keyType, "keyType must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
        Function<Map<K, V>, Map<K, V>> snapshotter = source -> {
            Objects.requireNonNull(source, "registered data must not be null");
            validateMap(name, source, keyType, valueType);
            return SnapshotSupport.snapshot(source);
        };
        return new RegistryKey<>(name, snapshotter, Function.identity());
    }

    public static <K1, K2, V> RegistryKey<Map<K1, Map<K2, V>>> nestedMap(
            String name, Class<K1> outerKeyType, Class<K2> innerKeyType, Class<V> valueType) {
        Objects.requireNonNull(outerKeyType, "outerKeyType must not be null");
        Objects.requireNonNull(innerKeyType, "innerKeyType must not be null");
        Objects.requireNonNull(valueType, "valueType must not be null");
        Function<Map<K1, Map<K2, V>>, Map<K1, Map<K2, V>>> snapshotter = source -> {
            Objects.requireNonNull(source, "registered data must not be null");
            for (Map.Entry<?, ?> outer : source.entrySet()) {
                validateType(name, outerKeyType, outer.getKey(), "outer map key");
                if (!(outer.getValue() instanceof Map<?, ?> inner)) {
                    throw new IllegalArgumentException(
                            "Registry key '" + name + "' requires each outer value to be a Map");
                }
                validateMap(name, inner, innerKeyType, valueType);
            }
            return SnapshotSupport.snapshot(source);
        };
        return new RegistryKey<>(name, snapshotter, Function.identity());
    }

    public String name() {
        return name;
    }

    T snapshot(T value) {
        return snapshotter.apply(value);
    }

    boolean requiresDefensiveCopy(T value) {
        return SnapshotSupport.requiresDefensiveCopy(value);
    }

    T expose(T value, boolean defensiveCopy) {
        return defensiveCopy
                ? SnapshotSupport.expose(value, true)
                : exposer.apply(value);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Registry key name must not be blank");
        }
        return name;
    }

    private static void validateElements(
            String name, Collection<?> values, Class<?> elementType, String role) {
        for (Object value : values) {
            validateType(name, elementType, value, role);
        }
    }

    private static void validateMap(
            String name, Map<?, ?> values, Class<?> keyType, Class<?> valueType) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            validateType(name, keyType, entry.getKey(), "map key");
            validateType(name, valueType, entry.getValue(), "map value");
        }
    }

    private static void validateType(
            String name, Class<?> expectedType, Object value, String role) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Registry key '" + name + "' does not allow a null " + role);
        }
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Registry key '" + name + "' requires " + role + " type "
                            + expectedType.getName() + " but received " + value.getClass().getName());
        }
    }

    private static IllegalArgumentException typeMismatch(
            String name, Class<?> expectedType, Object value) {
        return new IllegalArgumentException(
                "Registry key '" + name + "' requires " + expectedType.getName()
                        + " but received " + value.getClass().getName());
    }

    @Override
    public String toString() {
        return "RegistryKey[name=" + name + "]";
    }
}
