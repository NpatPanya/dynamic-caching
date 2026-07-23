package com.bbl.cache.registry;

import java.util.Objects;

/**
 * Typed retrieval token for {@link CacheRegistry}.
 *
 * <p>A {@code CacheDescriptor} pairs a registration {@code name} with a
 * best-effort runtime witness for the cache's key and value types
 * ({@code Class<K>} / {@code Class<V>}). It gives callers compile-time type
 * safety at the retrieval site (no cast) and a best-effort runtime check
 * that fails clearly with an {@link IllegalStateException} on mismatch,
 * rather than a silent {@link ClassCastException} at an unrelated call site.
 *
 * <p><b>Known limitation (Java type erasure).</b> The {@code Class<V>}
 * witness can only distinguish <em>raw</em> types. A grouped
 * {@code Cache<K, List<String>>} and a {@code Cache<K, List<Integer>>} both
 * carry {@code List.class} as their value witness and are indistinguishable
 * at retrieval. This is inherent to erasure, not a defect of this class.
 *
 * <p>Equality is defined over the exact tuple {@code (name, keyType,
 * valueType)} using {@link Object#equals(Object)} semantics -- never
 * {@link Class#isAssignableFrom(Class)} or any subtype/widening check.
 *
 * @deprecated Use string-keyed {@link CacheRegistry} methods with strict assignment types.
 */
@Deprecated
public final class CacheDescriptor<K, V> {

    private final String name;
    private final Class<K> keyType;
    private final Class<V> valueType;

    private CacheDescriptor(String name, Class<K> keyType, Class<V> valueType) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.keyType = Objects.requireNonNull(keyType, "keyType must not be null");
        this.valueType = Objects.requireNonNull(valueType, "valueType must not be null");
    }

    public static <K, V> CacheDescriptor<K, V> of(String name, Class<K> keyType, Class<V> valueType) {
        return new CacheDescriptor<>(name, keyType, valueType);
    }

    public String name() {
        return name;
    }

    public Class<K> keyType() {
        return keyType;
    }

    public Class<V> valueType() {
        return valueType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CacheDescriptor<?, ?> other)) {
            return false;
        }
        return name.equals(other.name)
                && keyType.equals(other.keyType)
                && valueType.equals(other.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, keyType, valueType);
    }

    @Override
    public String toString() {
        return "CacheDescriptor[name=" + name + ", keyType=" + keyType.getName()
                + ", valueType=" + valueType.getName() + "]";
    }
}
