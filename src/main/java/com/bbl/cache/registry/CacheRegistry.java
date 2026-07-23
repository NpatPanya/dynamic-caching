package com.bbl.cache.registry;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-wide registry of named caches.
 *
 * <p>Two retrieval paths are supported:
 *
 * <ul>
 *     <li><b>Type-safe (recommended for new code):</b> {@link #register(CacheDescriptor, Cache)}
 *     / {@link #get(CacheDescriptor)}. The descriptor carries a best-effort
 *     runtime witness for the key/value types, so a mismatched retrieval
 *     fails clearly with an {@link IllegalStateException} naming the
 *     expected vs. actual types, rather than a silent
 *     {@link ClassCastException} at an unrelated call site.</li>
 *     <li><b>Back-compat / simple (unchecked, documented unsafe):</b>
 *     {@link #register(String, Cache)} / {@link #get(String)}. The type
 *     parameters supplied to {@link #get(String)} cannot be verified at
 *     runtime; callers must use the same key/value types used at
 *     registration.</li>
 * </ul>
 *
 * <p>{@code register(...)} is fail-fast on a duplicate key (preserves
 * exactly-one-winner race semantics). {@link #reregister} is a documented
 * upsert that atomically replaces the stored entry -- it creates the entry
 * when absent and swaps it wholesale when present, building a fresh
 * {@link TtlCache} with a fresh {@code registeredAt} when a TTL is supplied.
 * In-flight readers holding a previously-returned {@link Cache} reference
 * keep reading the old immutable snapshot safely.
 */
public final class CacheRegistry {

    private final ConcurrentHashMap<String, CacheEntry<?, ?>> registry =
            new ConcurrentHashMap<>();

    private CacheRegistry() {
    }

    public static CacheRegistry getInstance() {
        return Holder.INSTANCE;
    }

    // ---- Back-compat / simple path ----

    public <K, V> Cache<K, V> register(String name, Cache<K, V> delegate) {
        return registerInternal(name, delegate, Optional.empty(), null);
    }

    public <K, V> Cache<K, V> register(String name, Cache<K, V> delegate, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        return registerInternal(name, delegate, Optional.of(ttl), null);
    }

    /**
     * Retrieves a cache by name. The unchecked cast is safe only when callers
     * use the same key/value types supplied during registration.
     */
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> get(String name) {
        validateName(name);
        CacheEntry<?, ?> entry = registry.get(name);
        return entry == null
                ? Optional.empty()
                : Optional.of((Cache<K, V>) entry.cache());
    }

    // ---- Type-safe path ----

    public <K, V> Cache<K, V> register(CacheDescriptor<K, V> descriptor, Cache<K, V> cache) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return registerInternal(descriptor.name(), cache, Optional.empty(), descriptor);
    }

    public <K, V> Cache<K, V> register(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        return registerInternal(descriptor.name(), cache, Optional.of(ttl), descriptor);
    }

    /**
     * Retrieves a cache by descriptor. The stored witness is compared to the
     * requested descriptor by exact {@code equals} over
     * {@code (name, keyType, valueType)} -- never assignability.
     *
     * @throws IllegalStateException if the entry was registered without a
     *      type descriptor (via {@link #register(String, Cache)}), or if it
     *      was registered under a different {@code (keyType, valueType)}
     *      witness for the same name.
     */
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> get(CacheDescriptor<K, V> descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        CacheEntry<?, ?> entry = registry.get(descriptor.name());
        if (entry == null) {
            return Optional.empty();
        }
        CacheDescriptor<?, ?> storedWitness = entry.descriptor();
        if (storedWitness == null) {
            throw new IllegalStateException(
                    "Cache '" + descriptor.name()
                            + "' registered without a type descriptor; retrieve via get(String)");
        }
        if (!storedWitness.equals(descriptor)) {
            throw new IllegalStateException(
                    "Cache '" + descriptor.name() + "' registered as <"
                            + storedWitness.keyType().getName() + ", " + storedWitness.valueType().getName()
                            + ">, requested as <"
                            + descriptor.keyType().getName() + ", " + descriptor.valueType().getName() + ">");
        }
        return Optional.of((Cache<K, V>) entry.cache());
    }

    // ---- Atomic-replace reload (upsert) ----

    public <K, V> Cache<K, V> reregister(CacheDescriptor<K, V> descriptor, Cache<K, V> cache) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        return reregisterInternal(descriptor.name(), cache, Optional.empty(), descriptor);
    }

    public <K, V> Cache<K, V> reregister(
            CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        return reregisterInternal(descriptor.name(), cache, Optional.of(ttl), descriptor);
    }

    /** Full replace; drops any prior type witness -- the entry has no witness afterward. */
    public <K, V> Cache<K, V> reregister(String name, Cache<K, V> cache) {
        return reregisterInternal(name, cache, Optional.empty(), null);
    }

    /** Full replace; drops any prior type witness -- the entry has no witness afterward. */
    public <K, V> Cache<K, V> reregister(String name, Cache<K, V> cache, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        return reregisterInternal(name, cache, Optional.of(ttl), null);
    }

    public boolean unregister(String name) {
        validateName(name);
        return registry.remove(name) != null;
    }

    /** Removes all registrations without invalidating already returned caches. */
    public void clear() {
        registry.clear();
    }

    private <K, V> Cache<K, V> registerInternal(
            String name, Cache<K, V> delegate, Optional<Duration> ttl, CacheDescriptor<K, V> descriptor) {
        validateName(name);
        Objects.requireNonNull(delegate, "delegate must not be null");
        Cache<K, V> exposed = decorate(delegate, ttl);
        CacheEntry<K, V> candidate = new CacheEntry<>(exposed, ttl, descriptor);
        if (registry.putIfAbsent(name, candidate) != null) {
            throw new IllegalStateException("Cache name already registered: " + name);
        }
        return exposed;
    }

    private <K, V> Cache<K, V> reregisterInternal(
            String name, Cache<K, V> delegate, Optional<Duration> ttl, CacheDescriptor<K, V> descriptor) {
        validateName(name);
        Objects.requireNonNull(delegate, "delegate must not be null");
        Cache<K, V> exposed = decorate(delegate, ttl);
        CacheEntry<K, V> replacement = new CacheEntry<>(exposed, ttl, descriptor);
        registry.put(name, replacement);
        return exposed;
    }

    private static <K, V> Cache<K, V> decorate(Cache<K, V> delegate, Optional<Duration> ttl) {
        return ttl.<Cache<K, V>>map(duration -> new TtlCache<>(delegate, duration)).orElse(delegate);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache name must not be blank");
        }
    }

    private record CacheEntry<K, V>(Cache<K, V> cache, Optional<Duration> ttl, CacheDescriptor<K, V> descriptor) {
        private CacheEntry {
            Objects.requireNonNull(cache, "cache must not be null");
            Objects.requireNonNull(ttl, "ttl must not be null");
            // descriptor is intentionally nullable: string-registered entries carry no witness.
        }
    }

    private static final class Holder {
        private static final CacheRegistry INSTANCE = new CacheRegistry();
    }
}
