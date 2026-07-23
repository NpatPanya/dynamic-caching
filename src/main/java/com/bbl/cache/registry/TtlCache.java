package com.bbl.cache.registry;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Read-only, snapshot-level lazy-expiry decorator over a read-only
 * {@link Cache} delegate.
 *
 * <p>The whole snapshot expires as a unit: a single {@code registeredAt}
 * ticker reading is captured at construction, and every read checks
 * {@code now - registeredAt > ttlNanos}.
 *
 * <ul>
 *     <li>Not expired -&gt; delegate straight through.</li>
 *     <li>Expired -&gt; the empty view: {@code get} returns {@code null},
 *     {@code getOrDefault} returns the supplied default, {@code containsKey}
 *     returns {@code false}, {@code size} returns {@code 0},
 *     {@code isEmpty} returns {@code true}, {@code asMap} returns
 *     {@code Map.of()}.</li>
 * </ul>
 *
 * <p>There is no {@code invalidate}, no {@code put}, no delegate mutation of
 * any kind -- the delegate is an immutable snapshot with no per-key writes
 * after construction, so per-key write-time TTL is meaningless; the correct
 * granularity is the whole snapshot.
 *
 * <p><b>Reload semantics.</b> An expired cache reports empty until something
 * re-registers a fresh snapshot (see {@link CacheRegistry#reregister}); it
 * does not self-reload. This is a deliberate design choice, not a
 * regression -- see {@code docs/cache-registry-v3-design.md} section 1(b).
 *
 * @deprecated Supply TTL to {@link CacheRegistry#register(String, Object, java.time.Duration)}.
 */
@Deprecated
public final class TtlCache<K, V> implements Cache<K, V> {

    private final Cache<K, V> delegate;
    private final long ttlNanos;
    private final LongSupplier ticker;
    private final long registeredAt;

    public TtlCache(Cache<K, V> delegate, Duration ttl) {
        this(delegate, ttl, System::nanoTime);
    }

    TtlCache(Cache<K, V> delegate, Duration ttl, LongSupplier ticker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        try {
            this.ttlNanos = ttl.toNanos();
            if (ttlNanos == Long.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "ttl must be less than Long.MAX_VALUE nanoseconds");
            }
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("ttl is too large", ex);
        }
        this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
        this.registeredAt = ticker.getAsLong();
    }

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (isExpired()) {
            return null;
        }
        return delegate.get(key);
    }

    @Override
    public V getOrDefault(K key, V defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        if (isExpired()) {
            return defaultValue;
        }
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (isExpired()) {
            return false;
        }
        return delegate.containsKey(key);
    }

    @Override
    public int size() {
        if (isExpired()) {
            return 0;
        }
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        if (isExpired()) {
            return true;
        }
        return delegate.isEmpty();
    }

    @Override
    public Map<K, V> asMap() {
        if (isExpired()) {
            return Map.of();
        }
        return delegate.asMap();
    }

    private boolean isExpired() {
        return ticker.getAsLong() - registeredAt > ttlNanos;
    }
}
