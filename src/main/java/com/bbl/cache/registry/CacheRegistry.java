package com.bbl.cache.registry;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Application-wide registry for typed immutable data snapshots.
 *
 * <p>New code registers arbitrary values by string name and retrieves them using a strict assignment type.
 * The legacy {@link Cache}/{@link CacheDescriptor} overloads remain available
 * as deprecated migration adapters.
 */
@SuppressWarnings("deprecation")
public final class CacheRegistry {

    private final ConcurrentHashMap<String, RegistryEntry> registry = new ConcurrentHashMap<>();
    private final LongSupplier ticker;

    private CacheRegistry() {
        this(System::nanoTime);
    }

    CacheRegistry(LongSupplier ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    }

    public static CacheRegistry getInstance() {
        return Holder.INSTANCE;
    }

    // ---- Primary string-key raw-data path ----

    public <T> T register(String name, T data) {
        return registerStringValue(name, data, Optional.empty(), false);
    }

    public <T> T register(String name, T data, Duration ttl) {
        return registerStringValue(name, data, validatedTtl(ttl), false);
    }

    public <T> T reregister(String name, T data) {
        return registerStringValue(name, data, Optional.empty(), true);
    }

    public <T> T reregister(String name, T data, Duration ttl) {
        return registerStringValue(name, data, validatedTtl(ttl), true);
    }

    public <T> T get(String name) {
        return this.<T>find(name).orElseThrow(
                () -> new NoSuchElementException("No registry value named: " + name));
    }

    public <T> Optional<T> find(String name) {
        validateName(name);
        RegistryEntry entry = registry.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        if (!(entry instanceof StringValueEntry valueEntry)) {
            throw new IllegalStateException(
                    "Name '" + name + "' is registered through a deprecated registry API");
        }
        if (valueEntry.isExpired(ticker.getAsLong())) {
            registry.remove(name, valueEntry);
            return Optional.empty();
        }
        return Optional.of(exposeStringValue(valueEntry));
    }

    private <T> T registerStringValue(
            String name, T data, Optional<Duration> ttl, boolean replace) {
        validateName(name);
        Objects.requireNonNull(data, "data must not be null");
        T snapshot = SnapshotSupport.snapshot(data);
        StringValueEntry candidate = new StringValueEntry(
                snapshot,
                ticker.getAsLong(),
                ttl.map(Duration::toNanos),
                SnapshotSupport.requiresDefensiveCopy(snapshot));
        if (!replace) {
            if (registry.putIfAbsent(name, candidate) != null) {
                throw new IllegalStateException("Registry name already registered: " + name);
            }
        } else {
            registry.compute(name, (ignored, existing) -> candidate);
        }
        return SnapshotSupport.expose(snapshot, candidate.defensiveCopyOnRead());
    }

    @SuppressWarnings("unchecked")
    private static <T> T exposeStringValue(StringValueEntry entry) {
        return SnapshotSupport.expose(
                (T) entry.data(), entry.defensiveCopyOnRead());
    }
    // ---- Deprecated RegistryKey path ----

    @Deprecated
    public <T> T register(RegistryKey<T> key, T data) {
        return registerValue(key, data, Optional.empty(), false);
    }

    @Deprecated
    public <T> T register(RegistryKey<T> key, T data, Duration ttl) {
        return registerValue(key, data, validatedTtl(ttl), false);
    }

    @Deprecated
    public <T> T reregister(RegistryKey<T> key, T data) {
        return registerValue(key, data, Optional.empty(), true);
    }

    @Deprecated
    public <T> T reregister(RegistryKey<T> key, T data, Duration ttl) {
        return registerValue(key, data, validatedTtl(ttl), true);
    }

    @Deprecated
    public <T> Optional<T> get(RegistryKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        RegistryEntry entry = registry.get(key.name());
        if (entry == null) {
            return Optional.empty();
        }
        if (!(entry instanceof ValueEntry<?> valueEntry)) {
            throw new IllegalStateException(
                    "Name '" + key.name() + "' is registered through the deprecated Cache API");
        }
        requireSameKey(key, valueEntry.key());
        if (valueEntry.isExpired(ticker.getAsLong())) {
            registry.remove(key.name(), valueEntry);
            return Optional.empty();
        }
        return Optional.of(expose(key, valueEntry));
    }

    @Deprecated
    public boolean unregister(RegistryKey<?> key) {
        Objects.requireNonNull(key, "key must not be null");
        AtomicReference<Boolean> removed = new AtomicReference<>(false);
        registry.compute(key.name(), (name, existing) -> {
            if (existing == null) {
                return null;
            }
            if (!(existing instanceof ValueEntry<?> valueEntry)) {
                throw new IllegalStateException(
                        "Name '" + name + "' is registered through the deprecated Cache API");
            }
            requireSameKey(key, valueEntry.key());
            removed.set(true);
            return null;
        });
        return removed.get();
    }

    private <T> T registerValue(
            RegistryKey<T> key, T data, Optional<Duration> ttl, boolean replace) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(data, "data must not be null");
        T snapshot = key.snapshot(data);
        ValueEntry<T> candidate =
                new ValueEntry<>(key, snapshot, ticker.getAsLong(), ttl.map(Duration::toNanos),
                        key.requiresDefensiveCopy(snapshot));

        if (!replace) {
            if (registry.putIfAbsent(key.name(), candidate) != null) {
                throw new IllegalStateException("Registry name already registered: " + key.name());
            }
        } else {
            registry.compute(key.name(), (name, existing) -> {
                if (existing != null) {
                    if (!(existing instanceof ValueEntry<?> valueEntry)) {
                        throw new IllegalStateException(
                                "Name '" + name + "' is registered through the deprecated Cache API");
                    }
                    requireSameKey(key, valueEntry.key());
                }
                return candidate;
            });
        }
        return key.expose(snapshot, candidate.defensiveCopyOnRead());
    }

    @SuppressWarnings("unchecked")
    private static <T> T expose(RegistryKey<T> key, ValueEntry<?> entry) {
        return key.expose((T) entry.data(), entry.defensiveCopyOnRead());
    }

    private static void requireSameKey(RegistryKey<?> requested, RegistryKey<?> stored) {
        if (requested != stored) {
            throw new IllegalStateException(
                    "Registry name '" + requested.name()
                            + "' belongs to a different RegistryKey instance");
        }
    }

    // ---- Deprecated Cache-based migration path ----

    /** @deprecated Register raw data with {@link #register(String, Object)}. */
    @Deprecated
    public <K, V> Cache<K, V> register(String name, Cache<K, V> delegate) {
        return registerLegacy(name, delegate, Optional.empty(), null, false);
    }

    /** @deprecated Register raw data with {@link #register(String, Object, Duration)}. */
    @Deprecated
    public <K, V> Cache<K, V> register(String name, Cache<K, V> delegate, Duration ttl) {
        return registerLegacy(name, delegate, validatedTtl(ttl), null, false);
    }

    /** @deprecated Retrieve raw data with {@link #get(String)}. */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> getCache(String name) {
        validateName(name);
        RegistryEntry entry = registry.get(name);
        if (entry == null) {
            return Optional.empty();
        }
        if (!(entry instanceof LegacyEntry legacyEntry)) {
            throw new IllegalStateException(
                    "Name '" + name + "' is registered through the typed raw-data API");
        }
        return Optional.of((Cache<K, V>) legacyEntry.cache());
    }

    /** @deprecated Register raw data with {@link #register(String, Object)}. */
    @Deprecated
    public <K, V> Cache<K, V> register(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return registerLegacy(descriptor.name(), cache, Optional.empty(), descriptor, false);
    }

    /** @deprecated Register raw data with {@link #register(String, Object, Duration)}. */
    @Deprecated
    public <K, V> Cache<K, V> register(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return registerLegacy(descriptor.name(), cache, validatedTtl(ttl), descriptor, false);
    }

    /** @deprecated Retrieve raw data with {@link #get(String)}. */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> get(CacheDescriptor<K, V> descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        RegistryEntry entry = registry.get(descriptor.name());
        if (entry == null) {
            return Optional.empty();
        }
        if (!(entry instanceof LegacyEntry legacyEntry)) {
            throw new IllegalStateException(
                    "Name '" + descriptor.name() + "' is registered through the typed raw-data API");
        }
        CacheDescriptor<?, ?> storedWitness = legacyEntry.descriptor();
        if (storedWitness == null) {
            throw new IllegalStateException(
                    "Cache '" + descriptor.name()
                            + "' registered without a type descriptor; retrieve via getCache(String)");
        }
        if (!storedWitness.equals(descriptor)) {
            throw new IllegalStateException(
                    "Cache '" + descriptor.name() + "' registered as <"
                            + storedWitness.keyType().getName() + ", "
                            + storedWitness.valueType().getName() + ">, requested as <"
                            + descriptor.keyType().getName() + ", "
                            + descriptor.valueType().getName() + ">");
        }
        return Optional.of((Cache<K, V>) legacyEntry.cache());
    }

    /** @deprecated Replace raw data with {@link #reregister(String, Object)}. */
    @Deprecated
    public <K, V> Cache<K, V> reregister(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return registerLegacy(descriptor.name(), cache, Optional.empty(), descriptor, true);
    }

    /** @deprecated Replace raw data with {@link #reregister(String, Object, Duration)}. */
    @Deprecated
    public <K, V> Cache<K, V> reregister(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return registerLegacy(descriptor.name(), cache, validatedTtl(ttl), descriptor, true);
    }

    /** @deprecated Replace raw data with {@link #reregister(String, Object)}. */
    @Deprecated
    public <K, V> Cache<K, V> reregister(String name, Cache<K, V> cache) {
        return registerLegacy(name, cache, Optional.empty(), null, true);
    }

    /** @deprecated Replace raw data with {@link #reregister(String, Object, Duration)}. */
    @Deprecated
    public <K, V> Cache<K, V> reregister(String name, Cache<K, V> cache, Duration ttl) {
        return registerLegacy(name, cache, validatedTtl(ttl), null, true);
    }

    /** Removes one registration by name. */
    public boolean unregister(String name) {
        validateName(name);
        return registry.remove(name) != null;
    }

    /** Removes all registrations without mutating snapshots already returned to callers. */
    public void invalidateAll() {
        registry.clear();
    }

    /** @deprecated Use {@link #invalidateAll()}. */
    @Deprecated
    public void clear() {
        invalidateAll();
    }

    private <K, V> Cache<K, V> registerLegacy(
            String name,
            Cache<K, V> delegate,
            Optional<Duration> ttl,
            CacheDescriptor<K, V> descriptor,
            boolean replace) {
        validateName(name);
        Objects.requireNonNull(delegate, "delegate must not be null");
        Cache<K, V> exposed = ttl.<Cache<K, V>>map(duration ->
                new TtlCache<>(delegate, duration)).orElse(delegate);
        LegacyEntry candidate = new LegacyEntry(exposed, descriptor);
        if (!replace) {
            if (registry.putIfAbsent(name, candidate) != null) {
                throw new IllegalStateException("Cache name already registered: " + name);
            }
        } else {
            registry.compute(name, (ignored, existing) -> {
                if (existing != null && !(existing instanceof LegacyEntry)) {
                    throw new IllegalStateException(
                            "Name '" + name + "' is registered through a value API");
                }
                return candidate;
            });
        }
        return exposed;
    }

    private static Optional<Duration> validatedTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        try {
            if (ttl.toNanos() == Long.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "ttl must be less than Long.MAX_VALUE nanoseconds");
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("ttl is too large", ex);
        }
        return Optional.of(ttl);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache name must not be blank");
        }
    }

    private interface RegistryEntry {
    }

    private record StringValueEntry(
            Object data,
            long registeredAt,
            Optional<Long> ttlNanos,
            boolean defensiveCopyOnRead)
            implements RegistryEntry {

        private StringValueEntry {
            Objects.requireNonNull(data, "data must not be null");
            Objects.requireNonNull(ttlNanos, "ttlNanos must not be null");
        }

        private boolean isExpired(long now) {
            return ttlNanos.isPresent() && now - registeredAt > ttlNanos.orElseThrow();
        }
    }
    private record ValueEntry<T>(
            RegistryKey<T> key, T data, long registeredAt, Optional<Long> ttlNanos,
            boolean defensiveCopyOnRead)
            implements RegistryEntry {

        private ValueEntry {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(data, "data must not be null");
            Objects.requireNonNull(ttlNanos, "ttlNanos must not be null");
        }

        private boolean isExpired(long now) {
            return ttlNanos.isPresent() && now - registeredAt > ttlNanos.orElseThrow();
        }
    }

    private record LegacyEntry(Cache<?, ?> cache, CacheDescriptor<?, ?> descriptor)
            implements RegistryEntry {
        private LegacyEntry {
            Objects.requireNonNull(cache, "cache must not be null");
        }
    }

    private static final class Holder {
        private static final CacheRegistry INSTANCE = new CacheRegistry();
    }
}
