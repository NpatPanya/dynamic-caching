# Prompt: Java Cache Registry with Lazy TTL Expiration

## Context

We already have low-level cache implementations (unique cache, group cache, double-key cache)
built as internal data structures — not a Caffeine-style all-in-one library. The actual
requirement from the team is a **singleton registry** that manages named cache instances
across the app, plus **optional per-cache TTL** that can be assigned freely at registration
time. Caches without a TTL assigned must **never expire**.

This is infrastructure code: no third-party caching dependency (no Caffeine, no Guava Cache).
Concurrency correctness and a clean, minimal API matter more than feature breadth.

## Requirements

### 1. Singleton registry, concurrency-safe

- Implement `CacheRegistry` as a true singleton (eager holder idiom or enum singleton — avoid
  double-checked locking with plain fields; prefer the initialization-on-demand holder pattern
  or `enum` for guaranteed thread-safe lazy init without synchronization overhead).
- Backing store: `ConcurrentHashMap<String, CacheEntry<?, ?>>` keyed by a unique cache name.
- Registration must be atomic and idempotent-safe: use `computeIfAbsent` (or `putIfAbsent` +
  discard) so two threads racing to register the same name never both win, and neither
  silently overwrites an existing cache. Decide and document behavior on name collision:
  either throw `IllegalStateException` on duplicate registration, or return the existing
  instance — pick one, don't do both silently.
- All public methods (`register`, `get`, `unregister`, `invalidate`) must be safe to call
  from arbitrary threads with no external locking required by callers.

### 2. Generic cache interface, pluggable backend

- Define one generic interface, e.g.:

  ```java
  public interface Cache<K, V> {
      V get(K key);
      V computeIfAbsent(K key, Function<K, V> loader);
      void put(K key, V value);
      void invalidate(K key);
      void invalidateAll();
      long size();
  }
  ```

- Existing unique/group/double-key caches become implementations (or are adapted via a thin
  wrapper) of this interface. The registry only ever stores and returns `Cache<K, V>`.
- TTL is a **decorator**, not a new cache type: `TtlCache<K, V> implements Cache<K, V>`,
  wrapping a delegate `Cache<K, V>` and adding expiration semantics. This keeps TTL orthogonal
  to which underlying cache strategy is used.

### 3. TTL: freely assignable per cache, lazy expiration, no TTL = never expires

- TTL is set **per cache instance at registration time**, not globally:

  ```java
  <K, V> Cache<K, V> register(String name, Cache<K, V> delegate);                 // no TTL — never expires
  <K, V> Cache<K, V> register(String name, Cache<K, V> delegate, Duration ttl);    // TTL applied
  ```

- Internally, each stored entry wraps its value with a recorded write/access timestamp.
  Use `Optional<Duration>` (or a sentinel like `Duration.ZERO`/null meaning "no expiry" —
  pick one and document it clearly; recommend `Optional<Duration>` for clarity over a
  magic sentinel) so absence of TTL is an explicit, type-safe state, not an implicit default.
- **Lazy expiration only** (per your direction — no background sweeper thread):
    - On every `get`/`computeIfAbsent`, check `now - lastWriteTime > ttl` (if TTL present).
    - If expired: treat as a miss, remove the entry, and (for `computeIfAbsent`) invoke the
      loader to repopulate.
    - If no TTL was registered for that cache: skip the expiration check entirely — the
      value lives until explicitly invalidated or the process ends.
    - Each entry needs its own timestamp state (e.g. an `AtomicLong` epoch millis) so
      concurrent reads/writes don't corrupt the recorded time — avoid read-modify-write races
      on the timestamp itself.
- Clarify and implement one semantic explicitly: is TTL **write-based** (reset only on
  `put`/load) or **access-based** (reset on every `get`, i.e. sliding expiration)? Default
  recommendation: write-based (simpler, more predictable, matches most low-level cache
  expectations) unless the team says otherwise.

### 4. API surface (registry)

```java
public final class CacheRegistry {
    public static CacheRegistry getInstance();

    <K, V> Cache<K, V> register(String name, Cache<K, V> delegate);
    <K, V> Cache<K, V> register(String name, Cache<K, V> delegate, Duration ttl);

    <K, V> Optional<Cache<K, V>> get(String name);
    boolean unregister(String name);
    void clear(); // primarily for tests
}
```

- `get` returns the TTL-wrapped cache transparently if a TTL was assigned at registration —
  callers never need to know whether TTL is in play.
- Generics on retrieval are inherently unchecked (type erasure) — document that callers are
  responsible for using consistent `K, V` per name, and consider an unchecked-cast helper
  with a clear Javadoc warning, similar to how `Map<String, Object>` registries typically work.

### 5. Deliverables

- `Cache<K, V>` interface
- `TtlCache<K, V>` decorator implementing lazy, per-entry expiration
- `CacheRegistry` singleton with concurrency-safe register/get/unregister
- Unit tests covering:
    - concurrent registration race (only one winner per name)
    - TTL expiration triggers reload via `computeIfAbsent`
    - no-TTL cache never expires under simulated time advance
    - unregistering a cache mid-use doesn't corrupt in-flight readers
- No dependency on Caffeine/Guava — java.util.concurrent and java.time only.

## Open questions to confirm with the team before finalizing

1. Duplicate name on `register`: throw, or return existing instance?
2. TTL semantics: reset on write only, or on every access (sliding)?
3. Do they want TTL to be *changeable* after registration (re-register with new TTL), or
   fixed for the cache's lifetime?