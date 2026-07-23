# Cache Registry v3 — Low-Level Design

Status: DESIGN (decision-complete). No production `.java` changes are made by this
document; interface stubs below are the contract a developer implements against.

Authoritative requirement: `docs/requirement-v3.md`.
This brief reconciles that requirement with the conflicting partial implementation
already on disk and resolves all three open questions as firm decisions.

---

## 0. The one thing that makes this hard (read first)

The four core deliverables — `Cache<K,V>`, the factory, `CacheRegistry`, `TtlCache<K,V>`
— are **not independent**. They are coupled through a single constraint:

> The on-disk `TtlCache` evicts by **mutating its delegate** (`delegate.invalidate(key)`
> then re-`get`). If the public `Cache<K,V>` contract becomes read-only (the v3 target),
> that mutation path disappears and TTL must evict some other way.

Every decision in Section 1 is one answer to that coupling, not three separate answers.
The short version: **public `Cache` is read-only; caches are immutable snapshots; TTL
expires the whole snapshot lazily with zero delegate mutation; reload is an atomic
registry-level replace.** The mutable methods that the on-disk model exposed publicly are
demoted to a legacy sub-interface that only the deprecated inheritance shapes use.

---

## 1. THE CORE COUPLED DECISION

### 1(a) — Public `Cache<K,V>` is READ-ONLY (v3 target), with a demoted legacy mutable layer

**Decision.** The public contract is exactly the v3 target:

```java
public interface Cache<K, V> {
    V get(K key);
    V getOrDefault(K key, V defaultValue);
    boolean containsKey(K key);
    int size();
    boolean isEmpty();
    Map<K, V> asMap();
}
```

The on-disk mutable methods (`computeIfAbsent`, `put`, `invalidate`, `invalidateAll`) are
**removed from `Cache`** and **relocated to `KeyedCache<K,V> extends Cache<K,V>`**, which
becomes the *legacy mutable contract* implemented by `AbstractMapCache` and the three
deprecated shapes. New code never sees `KeyedCache`.

**Why read-only (justification).**
- The whole point of the v3 refactor is composition: a factory builds an immutable,
  fully-materialized snapshot (`Map`) and wraps it as `Cache<K,V>`; there is **no
  per-entry write path** for new-style caches. Loading is "build the map, register it,"
  not "put keys one at a time." The mutable methods were the *inheritance-era loading
  mechanism* (`load`/`publish`/`computeIfAbsent`), now replaced by factory-build +
  registry-register. Keeping mutation public re-introduces exactly the coupling we are
  removing.
- The existing internal representation is already snapshot/copy-on-write
  (`AbstractMapCache.storedCache` is a `volatile Map` replaced wholesale). Read-only is the
  honest public shape of what the data already is.

**Why the layering (not "just delete the mutable methods").** `AbstractMapCache` carries
`@Override` annotations on `computeIfAbsent`/`put`/`invalidate`/`invalidateAll`
(`AbstractMapCache.java:37,55,67,81`). If those methods are declared in no supertype, those
annotations stop compiling and the deprecated shapes break — violating the mandatory
backward-compat constraint. Relocating them to `KeyedCache` (which `AbstractMapCache`
already `implements`) keeps every `@Override` valid and keeps `CacheFacade`'s
`KeyedCache::invalidateAll` reference (`CacheFacade.java:39`) valid. So the layering is not
gold-plating; it is the minimum that satisfies "read-only public contract" **and** "deprecated
shapes keep compiling unchanged."

**Compatibility verified, not assumed.** Content greps for
`\.(put|computeIfAbsent|invalidate|invalidateAll)\(` across `src/main` **and** `src/test`
show **no must-not-change consumer or surviving test invokes a mutator through a
`Cache<>`-typed reference**:
- The domain caches (`ServiceProviderProfileCache`, `AnygwResponseMappingCache`, etc.) have
  their cache fields **commented out** — no live references.
- `example/ProviderCatalogCache`, `example/CompositeDoubleKeyCacheUsage`, and the shape tests
  (`UniqueCacheTest`, `GroupedCacheTest`, `DoubleKeyCacheTest`, `MultiShapeCacheTest`) declare
  **shape-typed** references (`UniqueCache`/`GroupedCache`/`DoubleKeyCache`), so any mutator
  call resolves through the shape / `KeyedCache`, which retains them. (The lone
  `UniqueCacheTest:97` `view.put(...)` is on an `asMap()` **Map** view asserting
  `UnsupportedOperationException`, not on a `Cache` — unaffected.)
- The only `Cache<K,V>`-typed mutator calls live in `TtlCache` (being replaced) and in
  `CacheRegistryV2Test`/`TtlCacheTest` (already scoped REWRITE/REPLACE).

Therefore read-only public `Cache` breaks nothing outside the files this design already
scopes as CHANGE/REPLACE.

### 1(b) — TTL evicts by snapshot-level lazy expiry, with ZERO delegate mutation

**Decision.** `TtlCache<K,V>` is a read-only decorator over a read-only `Cache<K,V>`
delegate. It captures `registeredAt` (a ticker reading) at construction and a positive
`ttlNanos`. **The whole snapshot expires as a unit**: on every read it checks
`now - registeredAt > ttlNanos`.

- **Not expired** → delegate straight through.
- **Expired** → return the *empty view*: `get` → `null`, `getOrDefault` → the default,
  `containsKey` → `false`, `size` → `0`, `isEmpty` → `true`, `asMap` → `Map.of()`.

No `invalidate`, no `put`, no delegate mutation of any kind. This is what dissolves the
coupling: because the delegate is an immutable snapshot with no per-key writes after
construction, per-key write-time TTL is meaningless — the correct granularity is the whole
snapshot, and "expired" is a pure read-time gate.

**This is not a weaker approximation of the on-disk behavior — it is the same expiry
semantics minus the mutation.** The snapshot is built atomically, so *every* key shares one
write time (`registeredAt`). Per-key write-time expiry and whole-snapshot expiry therefore
**coincide** for a snapshot cache; no expiry granularity is lost by moving to a snapshot-level
gate. The only real behavioral delta is the reload path (read-through → external
re-registration), addressed next.

**The reload delta is a deliberate semantic change from the on-disk `TtlCache`** (which did
per-key read-through reload via `delegate.invalidate` + `computeIfAbsent`). Under v3 an
expired cache **reports empty until something re-registers a fresh snapshot** — it does not
self-reload. That is by design, not a regression, and it is consistent with:
- *no-TTL-means-never-expires* — a cache registered without a `Duration` is stored
  undecorated, so no gate exists;
- *lazy, no sweeper* — expiry is evaluated only on access; nothing counts down in the
  background;
- the reload story in 1(c) — refresh is an external atomic re-registration, not an internal
  mutation.

(Optional future extension, explicitly out of scope now: a `TtlCache` variant taking a
`Supplier<Cache<K,V>>` to self-rebuild on expiry. Not needed — CDI beans reload via the
registry. Flagged so the "empty on expiry" behavior is not mistaken for a missing feature.)

### 1(c) — Reload / re-registration is ATOMIC REPLACE; `Cache` needs NO replace/reload hook

**Decision (open question #3).** Periodic reload replaces the `Cache` instance atomically at
the **registry entry** level. `Cache` remains purely read-only — **no package-private
`replace`/`reload` hook is added.** This is the same axis as (a)/(b): immutable snapshots,
swapped wholesale, never mutated in place.

Mechanics:
- `register(...)` stays **fail-fast** on a duplicate key (preserves the existing
  exactly-one-winner race semantics).
- A new `reregister(...)` performs an **atomic swap** of the stored entry
  (`ConcurrentHashMap.put`/`compute`), building a *fresh* `TtlCache` with a *fresh*
  `registeredAt` when a TTL is supplied. In-flight readers holding the previous `Cache`
  reference keep reading the old immutable snapshot safely (already asserted by
  `unregisterDoesNotCorruptAnInFlightReader`).
- **Upsert semantics (both overloads).** `reregister` is a **documented upsert**: it creates
  the entry when the key is absent and replaces it when present. It does **not** require a
  prior registration and never fail-fasts on absence — that is the deliberate contrast with
  fail-fast `register`.
- **Type-witness rule on `reregister(descriptor, …)`:** the swap **fully replaces** the
  stored entry, so the *new* descriptor's witness wins (a `reregister` may legitimately
  change nothing, since reload reuses the same descriptor). `reregister` does **not** enforce
  a match against the prior witness — it is an intentional replacement, not a typed lookup.
  (Typed *lookup* mismatch is enforced only on `get(descriptor)`, Section 2.)
- **Type-witness rule on `reregister(String, …)`:** consistent with "full replace," the
  string overload replaces the entry with **no witness** — it drops any descriptor the prior
  registration carried. A subsequent `get(descriptor)` on that key therefore throws
  "registered without a type descriptor" (per the Section 2 table), exactly as if the entry
  had originally been string-registered. This is intentional: a string-keyed replace makes no
  type claim, so it must not silently retain a stale witness from the previous entry.

A CDI bean that reloads on a schedule therefore does: build new snapshot via the factory →
`reregister(descriptor, freshCache[, ttl])`. No `Cache` mutation anywhere.

---

## 2. OPEN QUESTION #1 — Type safety on retrieval

**Decision.** Introduce a typed token `CacheDescriptor<K,V>` as the **type-safe path**, and
keep a documented-unchecked `get(String)` strictly for back-compat. **Drop** any
`get(String, Class<V>)` overload — it is redundant with the descriptor and is the overload
that creates an undefined mixing of retrieval styles.

Rationale for descriptor over a bare `Class<V>`:
- A `Class<V>` witness cannot express generic value types — grouped caches have value type
  `List<T>` and double-key caches `Map<K2,V>`, neither expressible as a `Class`. A descriptor
  gives **compile-time** type safety on retrieval (no cast in caller code), which is the real
  win, plus a best-effort runtime witness.
- It fails *clearly* at the retrieval site (an `IllegalStateException` naming expected vs
  actual), never a silent `ClassCastException` at an unrelated call site — which is the tested
  deliverable.

```java
public final class CacheDescriptor<K, V> {
    private final String name;
    private final Class<K> keyType;     // best-effort witness (raw for generic keys)
    private final Class<V> valueType;   // best-effort witness (e.g. List.class for grouped)

    private CacheDescriptor(String name, Class<K> keyType, Class<V> valueType) { ... }

    public static <K, V> CacheDescriptor<K, V> of(String name, Class<K> keyType, Class<V> valueType);

    public String name();
    public Class<K> keyType();
    public Class<V> valueType();
    // equals/hashCode over (name, keyType, valueType)
}
```

**Retrieval contract (every interaction defined — no open mixing):**

| Stored via | Retrieved via | Result |
|---|---|---|
| `register(descriptor D)` | `get(D')` where `D.name == D'.name` and types match | typed `Optional<Cache<K,V>>`, no cast risk |
| `register(descriptor D)` | `get(D')` same name, **different** key/value type | `IllegalStateException` — "key 'X' registered as <A,B>, requested as <C,D>" |
| `register(String)` (untyped) | `get(descriptor)` | `IllegalStateException` — "key 'X' registered without a type descriptor; retrieve via get(String)" |
| any | `get(String)` | unchecked `Optional<Cache<K,V>>` (documented unsafe — back-compat only) |
| absent | either | `Optional.empty()` |

**Witness comparison rule (exact `equals`, not assignability).** `get(descriptor)` matches
the stored witness by **exact `equals` over `(name, keyType, valueType)`** — the same tuple
`CacheDescriptor.equals/hashCode` is defined over. It does **not** use `isAssignableFrom` or
any subtype/widening check. This is a deliberate determinism choice: a witness match is an
exact-tuple identity test, so retrieval outcome does not depend on class-hierarchy direction
(e.g. registering under `ArrayList.class` and requesting `List.class`, or vice-versa, is a
mismatch, not a partial match). The `name` comparison is likewise exact string equality.

**Known limitation — erased generics share a raw witness (inherent, not a defect).** The
`Class<V>` witness only distinguishes **raw** types. Because Java erases generics, a grouped
`Cache<K, List<String>>` and a `Cache<K, List<Integer>>` both carry `List.class` as their
value witness and are therefore **indistinguishable** at retrieval — a descriptor with the
wrong element type slips through the runtime check (the compile-time generic on
`CacheDescriptor<K,V>` still guides callers, but the runtime witness cannot enforce it). This
is the same limitation the requirement's own `Class<V>` suggestion carries; the descriptor is
strictly better (it adds compile-time typing and clear cross-raw-type failure) but cannot
defeat erasure. Documented as a known limitation, not a bug to "fix": the fail-clear
guarantee holds across **differing raw types**, which is the guarantee the deliverable-#7
type-mismatch test exercises.

`CacheDescriptor` is the **recommended** path for all new code. `register(String, …)` /
`get(String)` survive as the simple/back-compat path (and keep the illustrative
`register("key", cache)` usage and the untouched duplicate-race test compiling). The
type-mismatch row is the behavior exercised by the deliverable-#7 "type-mismatch retrieval"
test.

---

## 3. OPEN QUESTION #2 — CacheFactory vs ViewFactory (three-way reconciliation)

Three artifacts are in play:
- `support.CacheFactory` — returns **raw** `Map<K,V>` / `Map<K,List<V>>` / `Map<K1,Map<K2,V>>`;
  duplicate keys throw `IllegalStateException` via a throwing merger. Used by the deprecated
  shapes' `stage(...)`.
- `support.ViewFactory` — returns **raw** `List`/`Map` *views* (filter/sort/map/group/unique)
  over already-materialized data. Used by `example/Extended.test()`.
- v3's illustrative `registry.CacheFactory` — returns **`Cache<K,V>`**.

**Decision — keep two responsibilities, rename to kill the name collision:**

1. **New builder `com.bbl.cache.registry.Caches`** (NOT named `CacheFactory`). Returns
   `Cache<K,V>`. This is the single public factory new code uses. It **delegates** to
   `support.CacheFactory` for the raw-map transforms (so duplicate-key
   `IllegalStateException` semantics are preserved *for free*, unchanged), then wraps the
   immutable map in the read-only `ImmutableMapCache` (Section 6).

   > Why rename instead of merging into `support.CacheFactory`? The deprecated shapes already
   > create a `registry → support` package dependency. Putting `Cache<K,V>`-returning methods
   > in `support.CacheFactory` would force `support → registry`, a package cycle. And two
   > public classes both literally named `CacheFactory` is precisely the "two names for one
   > responsibility" confusion the requirement warns against. Renaming the new one to `Caches`
   > removes both problems: no cycle, no import ambiguity.

2. **Keep `support.CacheFactory` unchanged** as the low-level raw-`Map` transform primitive.
   Not `@Deprecated` (it is still the delegate behind `Caches` and behind the deprecated
   shapes). Documented as "internal transform primitive; new code uses `Caches`."

3. **Keep `support.ViewFactory` unchanged and separate.** This is a *genuinely distinct*
   responsibility and the reason a full collapse is rejected: `ViewFactory` produces **raw
   derived collections** (`List<T>`, `Map<G,List<V>>`) for direct ad-hoc use — filter, sort,
   map, group, unique views over already-materialized data — with **no `Cache` wrapper and no
   registration**. `Caches` produces **registrable keyed `Cache<K,V>` lookup structures**.
   Different return contract, different purpose. `example/Extended` depends on `ViewFactory`,
   so it must stay regardless.

   The requirement's suggested "mutable-lifecycle vs read-only" distinction *collapses* (both
   `Caches` output and `ViewFactory` output are read-only, since we chose read-only `Cache`),
   but the "produces `Cache<K,V>` vs produces raw collections" distinction is real and is the
   one we keep.

The "filtered/derived list → cache" requirement is served by **composition**: caller filters
with `ViewFactory.filteredView(...)`, then indexes the result with `Caches.fromList(...)`.

**`Caches` final signatures** — see Section 6.

---

## 4. RECONCILIATION TABLE (every on-disk artifact)

| Artifact | Verdict | What changes |
|---|---|---|
| `registry/Cache.java` | **CHANGE** | Redefine to the v3 read-only contract: `get`, `getOrDefault`, `containsKey`, `int size()`, `isEmpty`, `asMap`. Remove `computeIfAbsent`/`put`/`invalidate`/`invalidateAll` (they move to `KeyedCache`). `size()` return type `long` → `int`. |
| `registry/KeyedCache.java` | **CHANGE** | Becomes the *legacy mutable* sub-interface: `KeyedCache<K,V> extends Cache<K,V>` now **declares** `computeIfAbsent`, `put`, `invalidate`, `invalidateAll`, plus `default void clear(){ invalidateAll(); }`. The read methods it used to declare (`containsKey`/`getOrDefault`/`isEmpty`/`asMap`) now live in `Cache`. Documented "legacy internal mutable contract; new code uses `Cache`." Not `@Deprecated` (avoids warning noise in non-deprecated `AbstractMapCache`). |
| `registry/AbstractMapCache.java` | **CHANGE (one line)** | `public long size()` → `public int size()` (`storedCache.size()` is already `int`). All `@Override`s remain valid: read methods now override `Cache`, write methods override `KeyedCache`. No other change. |
| `registry/UniqueCache.java` | **CHANGE (annotation only)** | Add `@Deprecated(since="v3")` + Javadoc `@deprecated` → `Caches.fromList`. Body unchanged; still compiles and runs. |
| `registry/GroupedCache.java` | **CHANGE (annotation only)** | `@Deprecated(since="v3")` + `@deprecated` → `Caches.groupedFromList`. |
| `registry/DoubleKeyCache.java` | **CHANGE (annotation only)** | `@Deprecated(since="v3")` + `@deprecated` → `Caches.doubleKeyedFromList`. |
| `registry/CacheFacade.java` | **CHANGE (annotation only)** | `@Deprecated(since="v3")` + `@deprecated` → `Caches` + `CacheRegistry`. Uses `KeyedCache` (retained) and `size()` via `Math.toIntExact(...)` which still compiles against `int`. Body unchanged. |
| `registry/TtlCache.java` | **REPLACE** | Reimplement as a read-only snapshot-level lazy-expiry decorator over read-only `Cache<K,V>` (Section 1(b)). Drops the per-key `writeTimes` map, the mutator overrides, and all `delegate.invalidate/put/computeIfAbsent` calls. Keeps: positive-TTL validation, `LongSupplier` ticker seam, `registeredAt`. |
| `registry/CacheRegistry.java` | **CHANGE** | Keep singleton (`Holder`), `ConcurrentHashMap` + `putIfAbsent` atomic fail-fast registration, TTL-as-decorator wrap, `clear()`, name validation. Add: descriptor-typed `register`/`get` (Section 2), `reregister(...)` atomic-replace (Section 1(c)). `CacheEntry` gains an optional `CacheDescriptor` witness. `get(String)` stays unchecked/documented. **These additions are purely additive — `CacheRegistry.java` itself contains no `Cache`-mutator calls, so it does not participate in the interface-flip atomic commit (see §8).** |
| `support/CacheFactory.java` | **KEEP** | Unchanged. Remains the raw-`Map` transform primitive that `Caches` delegates to (preserving duplicate-key `IllegalStateException`). Javadoc note: "internal; new code uses `registry.Caches`." |
| `support/ViewFactory.java` | **KEEP** | Unchanged. Distinct responsibility (raw derived collection views). `example/Extended` depends on it. |
| `registry/Caches.java` (new) | **CREATE** | The v3 factory returning `Cache<K,V>` (Section 6). |
| `registry/CacheDescriptor.java` (new) | **CREATE** | Typed retrieval token (Section 2). |
| `registry/ImmutableMapCache.java` (new) | **CREATE** | Package-private read-only `Cache<K,V>` over an immutable `Map` (Section 6). The concrete thing `Caches.fromMap` wraps. |

### Fate of the two untracked tests

- **`CacheRegistryV2Test.java` → REWRITE (partial).** The rework set is **exactly two**
  tests, both of which call `cache.put(...)` through a `Cache<>`-typed reference and therefore
  no longer compile against read-only `Cache`:
  `noTtlRegistrationReturnsDelegateAndNeverExpires` (`put` at line 49) and
  `unregisterDoesNotCorruptAnInFlightReader` (`put` at line 112). Rework both to build the
  cache from data via `Caches` and assert reads (the latter keeps its in-flight-reader
  structure with `put` replaced by a factory-built snapshot).
  **Preserve `singletonUsesOneIdentity`,
  `ttlRegistrationReturnsDecoratorAndRetrievalReturnsSameDecorator`,
  `duplicateRegistrationRaceHasExactlyOneWinner`, and `validatesNamesTtlAndDuplicatePolicy`
  unchanged** — none of them call any mutator (verified against source: the ttl-decorator test
  at lines 57-67 calls no `put`), so they compile and pass as-is under read-only `Cache`;
  `register(String, …)` stays fail-fast, so the race/validation logic is likewise untouched.
- **`TtlCacheTest.java` → REWRITE (REPLACE).** Every test asserts per-key write-time TTL via
  `put`/`computeIfAbsent` (`readsDoNotResetWriteBasedTtlButPutDoes`,
  `expirationTriggersReloadViaComputeIfAbsent`, `concurrentExpiredComputationsInvokeLoaderOnce`,
  etc.). None survive the snapshot-level model. Replace with snapshot-level assertions: build a
  populated snapshot, verify reads before `registeredAt + ttl`, verify empty-view semantics
  after, verify never-expire when no TTL, keep the positive-TTL / ticker-seam construction tests.

The other test files (`UniqueCacheTest`, `GroupedCacheTest`, `DoubleKeyCacheTest`,
`MultiShapeCacheTest`, `CacheFacadeTest`) exercise the deprecated shapes through shape-typed
references (verified by the `src/test` mutator grep) and continue to compile/pass unchanged
under the layering.

---

## 5. BACKWARD-COMPAT PLAN

**Soft deprecation only — no `forRemoval`.** Per the requirement, deprecation steers new code
away without committing to a removal timeline. `forRemoval=true` is deliberately **not** used
(no team-agreed removal date exists; adding it would emit stronger warnings and imply a
contract we have not made).

Targets (annotation + `@deprecated` Javadoc pointer, bodies left functional):

| Class | `@Deprecated(since=...)` | `@deprecated` Javadoc points to |
|---|---|---|
| `UniqueCache` | `since = "v3"` | "Use `Caches.fromList(...)` + `CacheRegistry` instead of subclassing." |
| `GroupedCache` | `since = "v3"` | "Use `Caches.groupedFromList(...)` + `CacheRegistry`." |
| `DoubleKeyCache` | `since = "v3"` | "Use `Caches.doubleKeyedFromList(...)` + `CacheRegistry`." |
| `CacheFacade` | `since = "v3"` | "Use `Caches` to build caches and `CacheRegistry` for discovery." |

(Confirm the project's actual version string for `since` at implementation time; `"v3"` is a
placeholder matching the requirement's `@Deprecated(since = "...")`.)

**Compile/run confirmations (grep-verified, not assumed):**
- `UniqueCacheUsage`, `DoubleKeyCacheUsage`, `CompositeDoubleKeyCacheUsage`,
  `example/ProviderCatalogCache`, `example/Extended` — all use shape-typed references; the
  shapes remain concrete and functional. They compile unchanged (only emit deprecation
  warnings, which is the intended nudge).
- Shape tests `UniqueCacheTest`/`GroupedCacheTest`/`DoubleKeyCacheTest`/`MultiShapeCacheTest`
  and `CacheFacadeTest` hold no `Cache<>`-typed mutator reference (the `src/test` grep
  confirms every mutator call is via a shape type or an `asMap()` `Map` view). They compile/pass
  unchanged.
- `CacheFacade`'s `KeyedCache::invalidateAll` and `Math.toIntExact(cache.size())` remain valid
  (`KeyedCache` retains `invalidateAll`; `size()` as `int` widens fine).
- `support.CacheFactory` / `support.ViewFactory` unchanged, so `stage(...)` in the shapes and
  `Extended.test()` compile unchanged.
- The commented-out domain-cache fields reference nothing live; unaffected.

---

## 6. FINAL INTERFACE SIGNATURES

```java
// ---- registry/Cache.java (CHANGE: read-only public contract) ----
public interface Cache<K, V> {
    V get(K key);
    V getOrDefault(K key, V defaultValue);
    boolean containsKey(K key);
    int size();
    boolean isEmpty();
    Map<K, V> asMap();   // immutable snapshot view
}

// ---- registry/KeyedCache.java (CHANGE: legacy mutable sub-interface) ----
// Implemented only by AbstractMapCache and the deprecated shapes. New code never uses this.
public interface KeyedCache<K, V> extends Cache<K, V> {
    V computeIfAbsent(K key, Function<? super K, ? extends V> loader);
    void put(K key, V value);
    void invalidate(K key);
    void invalidateAll();
    default void clear() { invalidateAll(); }
}

// ---- registry/ImmutableMapCache.java (CREATE: the read-only impl Caches produces) ----
final class ImmutableMapCache<K, V> implements Cache<K, V> {   // package-private
    private final Map<K, V> data;                              // immutable (Map.copyOf on entry)
    ImmutableMapCache(Map<K, V> data);                         // defensively copies to an immutable map
    // get/getOrDefault/containsKey/size/isEmpty delegate to data; asMap() returns data (immutable)
}

// ---- registry/Caches.java (CREATE: v3 factory, delegates to support.CacheFactory) ----
public final class Caches {
    private Caches() {}

    public static <T, K> Cache<K, T> fromList(List<T> values, Function<T, K> keyExtractor);
    public static <T, K, V> Cache<K, V> fromList(List<T> values, Function<T, K> keyExtractor,
                                                 Function<T, V> valueExtractor);
    public static <K, V> Cache<K, V> fromMap(Map<K, V> source);            // defensively copied (Map.copyOf), no re-keying

    public static <T, K> Cache<K, List<T>> groupedFromList(List<T> values, Function<T, K> keyExtractor);
    public static <T, K, V> Cache<K, List<V>> groupedFromList(List<T> values, Function<T, K> keyExtractor,
                                                              Function<T, V> valueExtractor);

    public static <T, K1, K2> Cache<K1, Map<K2, T>> doubleKeyedFromList(List<T> values,
                                                                        Function<T, K1> k1, Function<T, K2> k2);
    public static <T, K1, K2, V> Cache<K1, Map<K2, V>> doubleKeyedFromList(List<T> values,
                                                                           Function<T, K1> k1, Function<T, K2> k2,
                                                                           Function<T, V> valueExtractor);
    // Duplicate-key IllegalStateException is inherited unchanged from support.CacheFactory.
}

// ---- registry/CacheDescriptor.java (CREATE) ----  (see Section 2 for full body)
public final class CacheDescriptor<K, V> {
    public static <K, V> CacheDescriptor<K, V> of(String name, Class<K> keyType, Class<V> valueType);
    public String name();
    public Class<K> keyType();
    public Class<V> valueType();
}

// ---- registry/TtlCache.java (REPLACE: snapshot-level lazy expiry, no mutation) ----
public final class TtlCache<K, V> implements Cache<K, V> {
    public TtlCache(Cache<K, V> delegate, Duration ttl);          // ticker = System::nanoTime
    TtlCache(Cache<K, V> delegate, Duration ttl, LongSupplier ticker);   // test seam
    // captures registeredAt = ticker.getAsLong(); rejects zero/negative/overflow ttl.
    // each read: if (now - registeredAt > ttlNanos) return EMPTY-VIEW value, else delegate.
    //   get -> null | getOrDefault -> default | containsKey -> false
    //   size -> 0   | isEmpty -> true         | asMap -> Map.of()
    // NO invalidate/put/computeIfAbsent; NO delegate mutation.
}

// ---- registry/CacheRegistry.java (CHANGE) ----
public final class CacheRegistry {
    public static CacheRegistry getInstance();

    // Type-safe path (recommended for new code):
    public <K, V> Cache<K, V> register(CacheDescriptor<K, V> descriptor, Cache<K, V> cache);
    public <K, V> Cache<K, V> register(CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl);
    public <K, V> Optional<Cache<K, V>> get(CacheDescriptor<K, V> descriptor);   // throws IllegalStateException on type/name mismatch

    // Back-compat / simple path (unchecked, documented unsafe):
    public <K, V> Cache<K, V> register(String registryKey, Cache<K, V> cache);
    public <K, V> Cache<K, V> register(String registryKey, Cache<K, V> cache, Duration ttl);
    public <K, V> Optional<Cache<K, V>> get(String registryKey);                 // unchecked cast

    // Atomic-replace reload (open question #3); documented upsert (creates if absent);
    // fully replaces the entry + its type witness:
    public <K, V> Cache<K, V> reregister(CacheDescriptor<K, V> descriptor, Cache<K, V> cache);
    public <K, V> Cache<K, V> reregister(CacheDescriptor<K, V> descriptor, Cache<K, V> cache, Duration ttl);
    public <K, V> Cache<K, V> reregister(String registryKey, Cache<K, V> cache);       // drops witness -> no-witness
    public <K, V> Cache<K, V> reregister(String registryKey, Cache<K, V> cache, Duration ttl);

    public boolean unregister(String registryKey);
    public void clear();
    // register(...)   = fail-fast on duplicate (putIfAbsent -> IllegalStateException).
    // reregister(...) = atomic swap (put/compute), upsert; fresh TtlCache => fresh registeredAt;
    //                   descriptor overload: new witness wins; String overload: no witness.
}
```

Typical new usage:

```java
var descriptor = CacheDescriptor.of("provider-by-service", String.class, Provider.class);
Cache<String, Provider> cache = Caches.fromList(providers, p -> p.getServiceName());
CacheRegistry.getInstance().register(descriptor, cache, Duration.ofMinutes(10));
...
Cache<String, Provider> live = CacheRegistry.getInstance().get(descriptor).orElseThrow();
```

---

## 7. DELIVERABLES CROSS-CHECK (against requirement-v3 §Deliverables)

| # | Requirement deliverable | Where satisfied |
|---|---|---|
| 1 | `Cache<K,V>` generic, shape-agnostic interface | §1(a), §6 — read-only contract |
| 2 | Single factory (or two only if justified) | §3 — `registry.Caches` (single Cache-returning factory); `support.ViewFactory` kept for the distinct raw-view responsibility, distinction stated; `support.CacheFactory` kept as internal primitive |
| 3 | `CacheRegistry` singleton: mandatory key, optional per-registration TTL, type-aware, concurrency-safe | §1(c), §2, §6 — singleton preserved, descriptor type awareness, per-registration TTL decorator, `ConcurrentHashMap`+atomic registration |
| 4 | `TtlCache<K,V>` decorator, lazy, no-TTL-never-expires | §1(b), §6 — snapshot-level lazy expiry, undecorated when no TTL |
| 5 | `@Deprecated` + `@deprecated` on `UniqueCache`/`GroupedCache`/`DoubleKeyCache`/`CacheFacade`, left functional | §5 — soft deprecation table, no `forRemoval` |
| 6 | New example usage (construct-and-register), alongside existing | Add e.g. `example/ProviderCatalogRegistryUsage` using `Caches` + `CacheDescriptor` + `CacheRegistry`, without touching the existing inheritance examples (implementation task) |
| 7 | Unit tests: factory transforms; concurrent registration race; TTL expiry + never-expire; type-mismatch retrieval | Covered — see below |

**Test coverage map (deliverable #7):**
- *Factory transforms (list / map / filtered-list → correct shape):* new `CachesTest` —
  `fromList`, `fromList`+valueExtractor, `fromMap`, `groupedFromList`, `doubleKeyedFromList`;
  duplicate-key → `IllegalStateException`; filtered-list case via
  `ViewFactory.filteredView` → `Caches.fromList`.
- *Concurrent registration race:* keep
  `CacheRegistryV2Test.duplicateRegistrationRaceHasExactlyOneWinner` (unchanged —
  `register` stays fail-fast).
- *TTL expiry + never-expire-without-TTL:* rewritten `TtlCacheTest` (snapshot-level: reads
  before boundary succeed, empty-view after; ticker seam) + a registry-level never-expire
  case (register without `Duration` → delegate returned undecorated, reads survive a large
  simulated time jump).
- *Type-mismatch retrieval:* new `CacheRegistryV2Test` case — register under
  `CacheDescriptor.of("k", String.class, Provider.class)`, then `get` with a descriptor of a
  different value type under the same name → `IllegalStateException` with expected-vs-actual
  message (not a silent `ClassCastException`); plus the "string-registered entry retrieved via
  descriptor → `IllegalStateException`" defined-interaction case.

---

## 8. Risks / non-functional notes for later stages

- **Interface-flip is one atomic commit.** The read-only `Cache` change (§1(a)) is not
  independently landable: the *instant* `Cache` loses its mutators, four things stop compiling
  **simultaneously** — the old mutating `TtlCache` (before its §1(b) REPLACE), the two
  `CacheRegistryV2Test` mutator calls (lines 49 and 112), and **all of `TtlCacheTest`** (every
  case calls `put`/`computeIfAbsent`). Therefore the `Cache`/`KeyedCache` split, the `TtlCache`
  replacement, and the two test rewrites (`CacheRegistryV2Test` partial + `TtlCacheTest`
  replace) MUST land as a **single atomic commit** — no intermediate compiling state exists.
  By contrast, `CacheRegistry.java`'s descriptor/`reregister` additions call no `Cache` mutator
  and are **purely additive**; they are NOT part of the atomic commit and may land separately
  (before or after) without breaking compilation. `task-planner` should sequence accordingly.
- **Concurrency:** correctness rests on immutable snapshots + `ConcurrentHashMap`. `TtlCache`
  no longer needs its `ReentrantLock` (no cross-field mutation); expiry is a lock-free read of
  two `final` longs. `architecture-engineer` should confirm no lock is reintroduced.
- **`asMap()` immutability:** `Cache.asMap()` must return an immutable map (factory outputs are
  already immutable; `ImmutableMapCache` must not expose a mutable reference). Callers rely on
  snapshot semantics.
- **`registeredAt` monotonicity:** use `System.nanoTime()` (monotonic) via the injected
  `LongSupplier`, not wall-clock, so TTL is immune to clock adjustment.
- **Type-witness is raw-only (erasure limitation):** §2 documents that the `Class<V>` witness
  distinguishes only raw types; mismatched generic element types (e.g. `List<String>` vs
  `List<Integer>`) share `List.class` and pass the runtime check. This is inherent to Java
  erasure and is a known limitation, not a defect for `architecture-engineer` to "solve."
- **`needs_clarification` — none blocking.** No decision here required inventing an unstated
  usage fact. One product-level assumption is surfaced, not guessed: an expired TTL cache
  reports **empty** until re-registered (it does not self-reload). If the team actually wants
  self-reloading TTL caches, that is a scoped extension (`TtlCache` + `Supplier<Cache<K,V>>`)
  and should be raised before implementation — but requirement-v3 describes reload as
  external (CDI bean + registry), so the empty-until-reregister behavior is the correct default.
