# Prompt: Refactor Cache Registry — Composition over Inheritance, Registry-Driven, Factory-Built

## Context

Current codebase (`com.bbl.cache.registry` / `com.bbl.cache.support`):

- `UniqueCache<K,V>`, `GroupedCache<K,V>`, `DoubleKeyCache<K1,K2,V>` are **abstract base
  classes**. Concrete caches (e.g. `UniqueCacheUsage`, `DoubleKeyCacheUsage`,
  `CompositeDoubleKeyCacheUsage`) *extend* them, call a protected `load(...)`, and are wired
  as CDI `@ApplicationScoped` beans.
- `CacheFactory` is a stateless static utility that transforms a `Collection<T>` into
  `Map<K,V>`, `Map<K,List<V>>`, or `Map<K1,Map<K2,V>>` using key/value extractor functions.
- There is no registry yet, and no `CacheFacade` in the codebase today.

This refactor changes the model from **inheritance-based cache subclasses** to
**composition: standalone cache objects built by a factory/transformer and registered by a
mandatory key**. This supersedes the earlier singleton-registry-with-TTL design — fold TTL
support into this new shape rather than treating it separately.

**This is a deprecation, not a deletion.** `CacheFacade` (if/when introduced) and the
existing `UniqueCache`, `GroupedCache`, `DoubleKeyCache` abstract classes should be marked
`@Deprecated` and left in place and functional, so existing subclasses
(`UniqueCacheUsage`, `DoubleKeyCacheUsage`, `CompositeDoubleKeyCacheUsage`, etc.) keep
compiling and running unchanged. New cache usages should use the
factory/registry pattern; old ones are migrated opportunistically, not in this change.

## Goals

1. **Mark `CacheFacade` as deprecated** (do not build new orchestration on top of it, and if
   any indirection resembling a facade exists today, annotate it `@Deprecated` with a Javadoc
   pointer to the factory/registry replacement rather than removing it outright). Each cache
   type must be constructible and initializable in isolation going forward — no shared facade
   orchestrating creation for new code.
2. **Each cache type initializes independently.** New code should not need extension or a
   shared base class lifecycle to get a working cache. A caller builds one directly (e.g. via
   `CacheFactory`/`ViewFactory`) and it's immediately usable — no subclass, no CDI bean
   required.
3. **Mark `UniqueCache`, `GroupedCache`, `DoubleKeyCache` as `@Deprecated`**, with Javadoc
   pointing to their replacement (`CacheFactory`/`ViewFactory` producing `Map<K,V>`,
   `Map<K,List<V>>`, `Map<K1,Map<K2,V>>` wrapped in the new generic `Cache<K,V>`). Do not
   remove or break them — existing subclasses must continue to compile and function as-is.
4. **`CacheFactory` (or `ViewFactory`) becomes the transformer** that turns raw input data
   into a ready-to-use cache object. It must accept, at minimum:
    - `List<T>` + key extractor(s) → keyed cache view
    - `Map<K,V>` (already-keyed data) → wrapped directly, no re-keying
    - a filtered/derived `List<T>` (i.e. caller pre-filters, factory just indexes the result)

   The distinction between `CacheFactory` and `ViewFactory` (if both are kept) should be
   made explicit: e.g. `CacheFactory` builds the registrable, mutable-lifecycle cache object;
   `ViewFactory` builds a read-only `KeyedCache`-style view over already-materialized data.
   If there's no real difference in practice, **collapse to one factory** — don't keep two
   names for the same responsibility.
5. **Registry requires an explicit `registryKey` on every registration — no default/anonymous
   registration path.** Registration without a key must not compile or must fail fast
   (prefer a compile-time signature that makes the key mandatory over a runtime check).
6. **Registry is aware of the data type it holds per key.** When storing a cache under
   `registryKey`, the registry should retain enough type information (e.g. a `Class<V>` or
   a small `CacheDescriptor<K,V>` token) to support type-safe retrieval, or at least to fail
   clearly (not with a silent `ClassCastException` at some unrelated call site) if a caller
   requests the wrong type back for a given key.

## Preserve from the earlier design (do not redo, just adapt)

- Registry is a **singleton**, concurrency-safe (`ConcurrentHashMap` + atomic
  `computeIfAbsent`-style registration, no double-checked locking).
- TTL is assignable **per registered cache, at registration time**, freely optional.
  A cache registered **without** a TTL **never expires**. Expiration is **lazy** (checked
  on access, no background sweeper thread), consistent with what was already agreed.
- TTL should still be implemented as a decorator/wrapper concern, not baked into each cache
  shape — it should apply uniformly regardless of whether the underlying shape is
  unique/grouped/double-keyed.

## Target shape (illustrative — adjust names as needed, but keep the constraints above)

```java
// One generic interface — all shapes (unique, grouped, double-key) implement this.
public interface Cache<K, V> {
    V get(K key);
    V getOrDefault(K key, V defaultValue);
    boolean containsKey(K key);
    int size();
    boolean isEmpty();
    Map<K, V> asMap();
}

// Factory builds cache objects directly from data — no subclassing required.
public final class CacheFactory {
    private CacheFactory() {}

    public static <T, K> Cache<K, T> fromList(List<T> values, Function<T, K> keyExtractor);
    public static <T, K, V> Cache<K, V> fromList(List<T> values, Function<T, K> keyExtractor, Function<T, V> valueExtractor);
    public static <K, V> Cache<K, V> fromMap(Map<K, V> source); // already-keyed data, wrapped as-is

    public static <T, K> Cache<K, List<T>> groupedFromList(List<T> values, Function<T, K> keyExtractor);
    public static <T, K1, K2> Cache<K1, Map<K2, T>> doubleKeyedFromList(List<T> values, Function<T, K1> k1, Function<T, K2> k2);
    // ...value-extractor overloads as today's CacheFactory already provides
}

// Registry: mandatory key, optional TTL, type-aware.
public final class CacheRegistry {
    public static CacheRegistry getInstance();

    <K, V> Cache<K, V> register(String registryKey, Cache<K, V> cache);
    <K, V> Cache<K, V> register(String registryKey, Cache<K, V> cache, Duration ttl);

    <K, V> Optional<Cache<K, V>> get(String registryKey);
    boolean unregister(String registryKey);
}
```

Usage becomes, roughly:

```java
var cache = CacheFactory.fromList(entities, e -> e.getId().getServiceName());
CacheRegistry.getInstance().register("entitiy2-by-service-name", cache);
```

instead of extending `UniqueCache<String, Entitiy2>` and calling `load(...)` from an
`@ApplicationScoped` bean's `init()`.

## Migration notes

- `AbstractMapCache` and `KeyedCache` may be reused as the internal representation behind
  `Cache<K,V>`, but should not be the public extension point for new code — new caches
  should not need to `extends` a cache class.
- Existing usage classes (`UniqueCacheUsage`, `DoubleKeyCacheUsage`,
  `CompositeDoubleKeyCacheUsage`) are **left as-is** for this change; they keep extending the
  now-`@Deprecated` base classes. Only *new* cache usages adopt the
  construct-via-factory-and-register pattern. Where CDI lifecycle (`@Inject` repo,
  `@ApplicationScoped` load-on-startup) is needed for a new cache, that becomes a thin bean
  that calls the factory + registry — it does not itself become a `Cache` implementation.
- Keep `CacheFactory`'s existing duplicate-key behavior (throw `IllegalStateException` via
  the throwing merger) — don't silently change that semantic during this refactor.
- Add `@Deprecated(since = "...")` (and `@deprecated` Javadoc tag with a pointer to the
  replacement) on `CacheFacade` (if present), `UniqueCache`, `GroupedCache`, and
  `DoubleKeyCache`. Do not add `forRemoval = true` unless the team has committed to a removal
  timeline — default to a soft deprecation that just steers new code away.

## Deliverables

1. `Cache<K,V>` interface (generic, shape-agnostic).
2. `CacheFactory` (single factory, or `CacheFactory` + `ViewFactory` only if a genuine
   distinction is justified — state the distinction explicitly if kept).
3. `CacheRegistry` singleton: mandatory `registryKey`, optional per-registration TTL, type
   awareness on stored entries, concurrency-safe.
4. `TtlCache<K,V>` decorator (lazy expiration, no-TTL-means-never-expires).
5. `@Deprecated` annotations (plus Javadoc `@deprecated` pointers to the replacement) on
   `UniqueCache`, `GroupedCache`, `DoubleKeyCache`, and any `CacheFacade` — left functional,
   not removed.
6. New example usage(s) showing the construct-and-register pattern, added alongside (not
   replacing) the existing inheritance-based examples.
7. Unit tests: factory transforms (list/map/filtered-list → correct shape), registry
   concurrent registration race, TTL expiry + never-expire-without-TTL, type-mismatch
   retrieval behavior.

## Open questions to confirm with the team before finalizing

1. Type-safety on retrieval: is a documented unchecked-cast contract acceptable, or do we
   need `CacheRegistry.get(registryKey, Class<V> valueType)` to enforce/verify type at
   runtime?
2. Do `CacheFactory` and `ViewFactory` need to be two separate classes, or is one factory
   sufficient? (Recommend collapsing to one unless there's a real read-only-view vs.
   mutable-registrable distinction in how they'll be used.)
3. For CDI-managed caches that reload periodically (e.g. on a schedule), does re-registering
   under the same `registryKey` replace the existing cache atomically, or should reload be
   an in-place mutation on the same `Cache` instance? (Affects whether `Cache` needs a
   package-private `replace`/`reload` hook alongside the public read-only interface.)