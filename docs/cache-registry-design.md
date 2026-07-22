# Multi-Shape Cache Design (Composition over Inheritance)

**Status:** Design spec (architecture). Implementation to follow via `task-planner` decomposition.
**Scope:** `src/main/java/com/bbl/cache/` — the `registry`/`support`/`example` packages.
**Author role:** architecture-engineer.

---

## 1. Problem statement

Today the three cache "shapes" are **abstract base classes** a usage bean must `extend`:

| Shape | Class | Stored structure |
|-------|-------|------------------|
| Unique key | `UniqueCache<K,V> extends AbstractMapCache<K,V>` | `Map<K,V>` |
| Grouped (one-to-many) | `GroupedCache<K,V> extends AbstractMapCache<K,List<V>>` | `Map<K,List<V>>` |
| Two-level key | `DoubleKeyCache<K1,K2,V> extends AbstractMapCache<K1,Map<K2,V>>` | `Map<K1,Map<K2,V>>` |

Because a bean acquires a shape by **extending** one of these, and Java allows only single
inheritance, **one bean can only ever be one shape**. The requirement is that a single bean
(e.g. `FooCache`) expose a unique view, a grouped view, and a double-key view simultaneously —
possibly over the same source collection, possibly over different loads.

### Root cause (precise)

The abstractness of `AbstractMapCache` is **not** the problem. The problem is that the *bean* is
forced into the inheritance chain. If each shape becomes a **self-contained object the bean holds
as a field**, the bean extends nothing and can hold as many shapes as it likes. Internal
inheritance *among* the three shape classes (sharing `AbstractMapCache`) stays DRY and is invisible
to beans.

---

## 2. Design overview

1. `AbstractMapCache<K,S>` stays the shared base of the three shapes, but:
   - It is `sealed` and `permits` only the three shapes.
   - It no longer relies on the *subclass* to supply `logger()`/`cacheName()`. Instead it takes a
     **`name` and a `Logger` via constructor** — so each shape instance is independently
     identifiable in logs.
   - It gains a protected `publish(Map)` swap primitive to support two-phase (build-then-publish)
     refresh.
2. `UniqueCache`, `GroupedCache`, `DoubleKeyCache` become **`final`, publicly-constructable**
   classes with **public** `load(...)` and a `stage(...)`/`publish(...)` pair. They are plain
   POJOs the bean instantiates — **not** CDI beans (no `@ApplicationScoped`, no proxying).
3. A new `CacheGroup` component provides central cross-cutting concerns (named registration,
   `clearAll`, `totalSize`, `sizes`). The bean **holds** a `CacheGroup` (composition) and creates
   its shape components through it, so they are registered automatically.
4. `CacheFactory` and `CacheUtils` are **unchanged**. Immutability of snapshots and
   duplicate-key-fails-fast are preserved for free because that logic lives entirely in
   `CacheFactory`.
5. `KeyedCache<K,V>` read contract is **unchanged**.

### Module boundaries

- **`registry/KeyedCache`** — read contract. Unchanged.
- **`registry/AbstractMapCache`** — sealed shared base: snapshot holding, read impl, logging,
  `publish`. Extended only by the three shapes.
- **`registry/UniqueCache|GroupedCache|DoubleKeyCache`** — final shape components. Own one
  `volatile` immutable snapshot each. Held as bean fields.
- **`registry/CacheGroup`** — *lifecycle/cross-cutting only*. Registers named components, exposes
  them as `KeyedCache<?,?>` for `clearAll`/`totalSize`/`sizes`. **It is not a read path** — all
  typed reads (including `DoubleKeyCache.get(k1,k2)`, which is not on `KeyedCache`) go through the
  bean's typed field, never through the group.
- **`support/CacheFactory`** — pure immutable-map builders. Unchanged, reused as-is.
- **`support/CacheUtils`** — `buildKeys` helper. Unchanged.
- **`example/*`** — usage beans. Migrated to hold fields instead of extending a shape.

---

## 3. Interface / class specifications

### 3.1 `KeyedCache<K,V>` (unchanged)

```java
public interface KeyedCache<K, V> {
    V get(K key);
    V getOrDefault(K key, V defaultValue);
    boolean containsKey(K key);
    void clear();
    int size();
    boolean isEmpty();
    Map<K, V> asMap();
}
```

Fix (pre-existing doc bug, do while here): the javadoc references a non-existent `BasicCache`.
Reword to "Populated via the shape components `UniqueCache` / `GroupedCache` / `DoubleKeyCache`."

### 3.2 `AbstractMapCache<K,S>` (sealed base)

```java
public sealed abstract class AbstractMapCache<K, S>
        implements KeyedCache<K, S>
        permits UniqueCache, GroupedCache, DoubleKeyCache {

    private final String name;
    private final Logger logger;
    protected volatile Map<K, S> storedCache = Map.of();

    protected AbstractMapCache(String name, Logger logger) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    // get / getOrDefault / containsKey / size / isEmpty / asMap / clear:
    //   BODIES UNCHANGED from current AbstractMapCache, except they now read
    //   the injected `logger`/`name` instead of the old abstract logger()/cacheName().

    protected final Logger logger()   { return logger; }   // was abstract; now final
    protected final String cacheName(){ return name; }     // was getClass().getSimpleName()

    /** Atomic single-volatile-write swap of the whole snapshot. Cannot throw. */
    protected final void publish(Map<K, S> snapshot) {
        this.storedCache = snapshot;
    }

    protected static void validate(Collection<?> c, Function<?, ?> keyExtractor) { /* unchanged */ }
    protected void logItem(String action, Object key, Object value)             { /* unchanged */ }
}
```

**Deliberate behavior change — flag, do not treat as silent:** `cacheName()` moves from
`getClass().getSimpleName()` to the constructor `name`. This is *necessary*: three shape fields on
one bean would otherwise all log as the same class name. TRACE log labels change from
e.g. `[UniqueCacheUsage]` to `[byServiceName]` (the field's registered name). This is the intended
behavior — component-level labels are more useful in a multi-shape bean. All other TRACE logging
(the GET/GET_OR_DEFAULT/CONTAINS_KEY/clear/load-count lines) is otherwise preserved verbatim.

### 3.3 `UniqueCache<K,V>` (final component)

```java
public final class UniqueCache<K, V> extends AbstractMapCache<K, V> {

    public UniqueCache(String name, Logger logger) { super(name, logger); }

    // ---- Build (pure, no mutation). Throws IllegalStateException on duplicate key. ----
    public Map<K, V> stage(Collection<V> c, Function<V, K> keyExtractor) {
        validate(c, keyExtractor);
        return CacheFactory.uniqueCache(c, keyExtractor);
    }
    public <T> Map<K, V> stage(Collection<T> c, Function<T, K> keyExtractor,
                               Function<T, V> valueExtractor) {
        validate(c, keyExtractor);
        Objects.requireNonNull(valueExtractor, "valueExtractor must not be null");
        return CacheFactory.uniqueCache(c, keyExtractor, valueExtractor);
    }

    // ---- Publish (swap + trace load count). ----
    public void publish(Map<K, V> snapshot) {
        super.publish(snapshot);
        logLoad();
    }

    // ---- Convenience: single-view atomic build+publish (== current load() behavior). ----
    public void load(Collection<V> c, Function<V, K> ke)                    { publish(stage(c, ke)); }
    public <T> void load(Collection<T> c, Function<T, K> ke, Function<T, V> ve) { publish(stage(c, ke, ve)); }

    private void logLoad() { /* trace "[name] Loaded {} cache entries" — unchanged */ }
}
```

Note: `load(...)` changes from `protected` to `public` (the bean now *calls* it rather than
*inheriting* it).

### 3.4 `GroupedCache<K,V>` (final component)

Same shape as `UniqueCache` but stored type is `List<V>` and builders call
`CacheFactory.groupCache`. Preserve the existing `get(K)` override:

```java
public final class GroupedCache<K, V> extends AbstractMapCache<K, List<V>> {

    public GroupedCache(String name, Logger logger) { super(name, logger); }

    public Map<K, List<V>> stage(Collection<V> c, Function<V, K> ke) {
        validate(c, ke);
        return CacheFactory.groupCache(c, ke);
    }
    public <T> Map<K, List<V>> stage(Collection<T> c, Function<T, K> ke, Function<T, V> ve) {
        validate(c, ke);
        Objects.requireNonNull(ve, "valueExtractor must not be null");
        return CacheFactory.groupCache(c, ke, ve);
    }
    public void publish(Map<K, List<V>> snapshot) { super.publish(snapshot); logLoad(); }
    public void load(Collection<V> c, Function<V, K> ke)                        { publish(stage(c, ke)); }
    public <T> void load(Collection<T> c, Function<T, K> ke, Function<T, V> ve) { publish(stage(c, ke, ve)); }

    /** Returns an empty immutable list when the key is absent. Never null. */
    @Override
    public List<V> get(K key) { return storedCache.getOrDefault(key, List.of()); }

    private void logLoad() { /* trace "[name] Loaded {} cache entries" — unchanged */ }
}
```

Fix while here (pre-existing): the current `get(K)` javadoc contains corrupted characters
(`associat*d`, `*f the key`, etc.) — restore to clean text. **Known quirk to preserve, not fix:**
this `get(K)` override does *not* call `logItem` (unlike the inherited `get`). Left as-is
intentionally; flagged so it is not mistaken for a regression.

### 3.5 `DoubleKeyCache<K1,K2,V>` (final component)

```java
public final class DoubleKeyCache<K1, K2, V> extends AbstractMapCache<K1, Map<K2, V>> {

    public DoubleKeyCache(String name, Logger logger) { super(name, logger); }

    public Map<K1, Map<K2, V>> stage(Collection<V> c, Function<V, K1> k1, Function<V, K2> k2) {
        validate(c, k1);
        Objects.requireNonNull(k2, "key2Extractor must not be null");
        return CacheFactory.doubleKeysCache(c, k1, k2);
    }
    public <T> Map<K1, Map<K2, V>> stage(Collection<T> c, Function<T, K1> k1,
                                         Function<T, K2> k2, Function<T, V> ve) {
        validate(c, k1);
        Objects.requireNonNull(k2, "key2Extractor must not be null");
        Objects.requireNonNull(ve, "valueExtractor must not be null");
        return CacheFactory.doubleKeysCache(c, k1, k2, ve);
    }
    public void publish(Map<K1, Map<K2, V>> snapshot) { super.publish(snapshot); logLoad(); }
    public void load(Collection<V> c, Function<V, K1> k1, Function<V, K2> k2) { publish(stage(c, k1, k2)); }
    public <T> void load(Collection<T> c, Function<T, K1> k1, Function<T, K2> k2, Function<T, V> ve) {
        publish(stage(c, k1, k2, ve));
    }

    /** Two-level lookup; null if either key is absent. Body unchanged from today. */
    public V get(K1 key1, K2 key2) { /* unchanged, incl. its own trace line */ }

    /** Returns Map.of() when the outer key is absent. */
    @Override
    public Map<K2, V> get(K1 key1) { return storedCache.getOrDefault(key1, Map.of()); }

    private void logLoad() { /* trace "[name] Loaded {} outer keys and {} cache entries" — unchanged */ }
}
```

`get(K1,K2)` is **not** on `KeyedCache` — it is reachable only through the bean's typed field, never
through `CacheGroup`.

### 3.6 `CacheGroup` (new — cross-cutting registry)

```java
public final class CacheGroup {

    private final String groupName;
    private final Logger logger;
    private final Map<String, KeyedCache<?, ?>> components = new LinkedHashMap<>();

    public CacheGroup(String groupName, Logger logger) {
        this.groupName = Objects.requireNonNull(groupName, "groupName");
        this.logger    = Objects.requireNonNull(logger, "logger");
    }

    // ---- Factory + auto-register. Throws on duplicate component name. ----
    public <K, V>       UniqueCache<K, V>          uniqueCache(String name)  { return reg(name, new UniqueCache<>(name, logger)); }
    public <K, V>       GroupedCache<K, V>         groupedCache(String name) { return reg(name, new GroupedCache<>(name, logger)); }
    public <K1, K2, V>  DoubleKeyCache<K1, K2, V>  doubleKeyCache(String name){ return reg(name, new DoubleKeyCache<>(name, logger)); }

    private <C extends KeyedCache<?, ?>> C reg(String name, C c) {
        if (components.putIfAbsent(name, c) != null)
            throw new IllegalStateException("duplicate cache component name: " + name);
        return c;
    }

    // ---- Cross-cutting operations ----
    public void clearAll() {
        components.values().forEach(KeyedCache::clear);
        if (logger.isTraceEnabled())
            logger.trace("[{}] cleared {} components", groupName, components.size());
    }
    public int totalSize()                { return components.values().stream().mapToInt(KeyedCache::size).sum(); }
    public Map<String, Integer> sizes()   { /* name -> size, preserving insertion order */ }
    public Set<String> names()            { return Collections.unmodifiableSet(components.keySet()); }
    public KeyedCache<?, ?> get(String name) { return components.get(name); } // read-only central view
}
```

Named `CacheGroup`, **not** `CacheRegistry`: the README already claims `CacheRegistry` for a
different (String-keyed) concept — avoid the collision.

---

## 4. Loading & atomicity semantics

The task asks whether individual loads must stay atomic when one bean owns several views loaded from
one fetch. Two distinct properties, resolved separately:

### 4.1 Failure atomicity — REQUIRED, guaranteed

A duplicate key (or any build error) in one view must **not** leave another view updated. Achieved
by **two-phase build-then-publish**:

1. **Stage (build) phase** — call `stage(...)` for every view. Only `CacheFactory` can throw
   (duplicate keys / null args). If any `stage` throws, no `publish` has run yet, so **no
   `storedCache` is mutated** — every view retains its previous snapshot.
2. **Publish phase** — call `publish(snapshot)` for every view. Each `publish` is a single volatile
   assignment that **cannot throw**. So once staging succeeds, all publishes succeed together.

The single-view `load(...)` convenience is just `publish(stage(...))` — identical to today's atomic
swap.

### 4.2 Cross-view read atomicity — NOT required, explicitly accepted

A concurrent reader can, for a sub-microsecond window during the publish loop, observe
new-view-A + old-view-B. This is **accepted** and consistent with the README's stated tolerance for
transient reload skew ("readers may transiently see fewer entries during a reload").

**Escape hatch (documented, not implemented — YAGNI):** if strict cross-view atomicity is ever
required, replace the per-component `volatile Map` with a single `volatile Snapshot` record held by
the bean/group that carries all views, published in one assignment. This is a deliberate decision
flagged for the team, not resolved silently — raise it if a consumer needs it.

### 4.3 Source must be materialized once

`stage(...)` streams the source collection **once per view**. A multi-view `refresh()` MUST fetch
into a stable, re-iterable `Collection` (e.g. a `List`) **before** staging. A lazy/single-pass
source would be exhausted by the first `stage` and silently produce empty later views. State this in
the bean's `refresh()` contract.

---

## 5. Field-initialization ordering (hard constraint)

Because shape fields are created *through* the `CacheGroup`, the `CacheGroup` field MUST be declared
**before** any component field that calls `caches.uniqueCache(...)` etc. Java runs field
initializers top-to-bottom; a component field declared above `caches` would NPE. This is a spec
constraint, not a style preference — implementers must not reorder.

```java
private final CacheGroup caches = new CacheGroup("FooCache", log); // FIRST
private final UniqueCache<String, E>  byId   = caches.uniqueCache("byId");   // after
private final GroupedCache<String, E> byType = caches.groupedCache("byType"); // after
```

---

## 6. Migration sketches

> The three current example beans reference entity/repo types not present in this module
> (`Entitiy1/2/3`, `Entitiy2RepositoryPort`, `ExampleRepo`, `Entitiy3RepositoryPort`). They are
> illustrative stubs. The sketches keep those names for continuity.

### 6.1 `UniqueCacheUsage` (single shape today, extensible tomorrow)

```java
@ApplicationScoped
public class UniqueCacheUsage {

    private static final Logger log = LogManager.getLogger();

    private final CacheGroup caches = new CacheGroup("UniqueCacheUsage", log);
    private final UniqueCache<String, Entitiy2> byServiceName = caches.uniqueCache("byServiceName");

    private Entitiy2RepositoryPort repo;

    public UniqueCacheUsage() { }

    @Inject
    public UniqueCacheUsage(Entitiy2RepositoryPort repo) { this.repo = repo; }

    public void init(List<String> serviceNameList) {
        var result = repo.findByServiceNameList(serviceNameList);
        byServiceName.load(result, s -> s.getId().getServiceName());
    }

    // reads now go through the field:
    public Entitiy2 byServiceName(String name) { return byServiceName.get(name); }
    public void clearAll() { caches.clearAll(); }
}
```

The bean no longer `extends` anything and no longer overrides `logger()`. Adding a second shape is
now a one-line field addition — no inheritance conflict.

### 6.2 `DoubleKeyCacheUsage`

```java
@ApplicationScoped
public class DoubleKeyCacheUsage {

    private static final Logger log = LogManager.getLogger();

    private final CacheGroup caches = new CacheGroup("DoubleKeyCacheUsage", log);
    private final DoubleKeyCache<String, String, Entitiy1> byServiceProvider =
            caches.doubleKeyCache("byServiceProvider");

    private ExampleRepo port;

    public DoubleKeyCacheUsage() { }

    @Inject
    public DoubleKeyCacheUsage(ExampleRepo port) { this.port = port; }

    public void init(List<String> serviceNameList) {
        var result = port.findByServiceNameList(serviceNameList);
        byServiceProvider.load(result,
                p -> p.getId().getServiceName(),
                p -> p.getId().getProviderId());
    }

    public Entitiy1 get(String serviceName, String providerId) {
        return byServiceProvider.get(serviceName, providerId); // typed field, not the group
    }
}
```

### 6.3 `CompositeDoubleKeyCacheUsage` (record composite keys — unchanged pattern)

```java
@ApplicationScoped
public class CompositeDoubleKeyCacheUsage {

    private static final Logger log = LogManager.getLogger();

    private final CacheGroup caches = new CacheGroup("CompositeDoubleKeyCacheUsage", log);
    private final DoubleKeyCache<ServiceClientKey, ProviderResponseKey, Entitiy3> byKeys =
            caches.doubleKeyCache("byKeys");

    private Entitiy3RepositoryPort port;

    public CompositeDoubleKeyCacheUsage() { }

    @Inject
    public CompositeDoubleKeyCacheUsage(Entitiy3RepositoryPort port) { this.port = port; }

    public void init(List<String> serviceNameList) {
        var result = port.findByServiceNameList(serviceNameList);
        byKeys.load(result,
                f -> new ServiceClientKey(f.getId().getServiceName(), f.getId().getClientId()),
                s -> new ProviderResponseKey(s.getId().getProviderId(), s.getId().getProviderRspcode()));
    }

    public Entitiy3 get(ServiceClientKey k1, ProviderResponseKey k2) { return byKeys.get(k1, k2); }

    public record ServiceClientKey(String serviceName, String clientId) { }
    public record ProviderResponseKey(String providerId, String providerRspCode) { }
}
```

### 6.4 NEW — multi-shape bean (proves the requirement)

One bean, three shapes, all built from a **single materialized fetch**, with **failure-atomic**
refresh:

```java
@ApplicationScoped
public class ProviderCatalogCache {

    private static final Logger log = LogManager.getLogger();

    private final CacheGroup caches = new CacheGroup("ProviderCatalogCache", log);

    // three shapes side by side — impossible under the old inheritance model
    private final UniqueCache<String, Provider>              byId       = caches.uniqueCache("byId");
    private final GroupedCache<String, Provider>             byCountry  = caches.groupedCache("byCountry");
    private final DoubleKeyCache<String, String, Provider>   byServiceProvider =
            caches.doubleKeyCache("byServiceProvider");

    private ProviderRepositoryPort repo;

    public ProviderCatalogCache() { }

    @Inject
    public ProviderCatalogCache(ProviderRepositoryPort repo) { this.repo = repo; }

    /** Failure-atomic refresh of all three views from one fetch. */
    public void refresh(List<String> serviceNames) {
        // 1. materialize ONCE (re-iterable) — see 4.3
        List<Provider> src = List.copyOf(repo.findByServiceNames(serviceNames));

        // 2. build all snapshots (any dup-key throws here, before any publish)
        var idMap       = byId.stage(src, Provider::id);
        var countryMap  = byCountry.stage(src, Provider::country);
        var svcProvMap  = byServiceProvider.stage(src, Provider::serviceName, Provider::id);

        // 3. publish — cannot throw; all views advance together
        byId.publish(idMap);
        byCountry.publish(countryMap);
        byServiceProvider.publish(svcProvMap);
    }

    // typed reads through the fields:
    public Provider       byId(String id)                          { return byId.get(id); }
    public List<Provider> byCountry(String country)                { return byCountry.get(country); }
    public Provider       byServiceProvider(String svc, String id) { return byServiceProvider.get(svc, id); }

    // cross-cutting through the group:
    public void clearAll()               { caches.clearAll(); }
    public Map<String,Integer> sizes()   { return caches.sizes(); }
}
```

---

## 7. Preserved behavior (verified, not incidental)

| Concern | How preserved |
|---------|---------------|
| `KeyedCache` read contract | Interface untouched. |
| Immutable snapshots | Produced entirely by `CacheFactory` (`toUnmodifiableMap`/`List.copyOf`/`unmodifiableMap`) — untouched. |
| Duplicate-key fails fast | `CacheFactory.throwingMerger()` — untouched. `stage()` surfaces the `IllegalStateException`. |
| TRACE logging | `logItem` + load-count traces preserved; only the label source changes (§3.2). |
| Per-view atomic swap | Single volatile write in `publish` — unchanged from today. |
| `GroupedCache.get` empty-list / `DoubleKeyCache.get(K1)` empty-map defaults | Overrides kept verbatim. |

---

## 8. Verification hooks (for `tester`)

Each hook is an observable assertion proving a component matches this spec. Shape components are
plain POJOs, so they are unit-testable with no CDI container.

**UniqueCache**
- `load` then `asMap().size()` equals input count; `get(k)` returns the loaded value.
- Two elements resolving to the same key → `stage`/`load` throws `IllegalStateException`.
- `asMap().put(...)` throws `UnsupportedOperationException` (immutability).
- `get`/`containsKey` emit a TRACE line only when trace is enabled (capture via test appender).

**GroupedCache**
- Multiple values under one key land in one `List`; that list is immutable (`add` throws).
- `get(missingKey)` returns an empty list (not null).

**DoubleKeyCache**
- `get(k1,k2)` returns the value; `get(k1)` on a missing outer key returns `Map.of()` (not null).
- Duplicate `(k1,k2)` pair → `stage`/`load` throws `IllegalStateException`.

**CacheGroup**
- Registering two components with the same name throws `IllegalStateException`.
- `clearAll()` empties every registered component (`totalSize()==0` afterward).
- `totalSize()`/`sizes()` sum/report registered component sizes; `names()` is unmodifiable.
- `get(name)` returns the same instance registered.

**Failure atomicity (the novel behavior — assert both directions)**
- *Positive:* successful `refresh` advances all three views (all `size()` reflect new data).
- *Negative:* seed all views, then `refresh` with a source that makes the **second** `stage` throw
  (duplicate key). Assert the **first** view still holds its **previous** snapshot — not empty, not
  the new data — and no view was partially updated.

**Field-init ordering**
- A bean that declares `caches` after a component field is a compile/runtime-order bug; covered by
  the working multi-shape example bean loading successfully at startup.

---

## 9. Pre-existing issues surfaced (out of scope for this design — flagged, not fixed here)

These predate this redesign; listed so they are tracked, not silently absorbed:

1. **Broken imports** — `UniqueCache`, `GroupedCache`, `DoubleKeyCache`, and
   `CompositeDoubleKeyCacheUsage` import `com.bbl.gw.config.cache.*` while declared in
   `com.bbl.cache.*`. Leftover from the "redisgn caching" rename. Must be corrected during
   implementation or nothing compiles.
2. **Missing example types** — `Entitiy1/2/3`, `Entitiy2RepositoryPort`, `ExampleRepo`,
   `Entitiy3RepositoryPort`, `AnygwResponseMappingRepositoryPort`, `Anygwresponsemapping` do not
   exist in the module; the example beans are non-compiling stubs today.
3. **Corrupted javadoc** — `GroupedCache.get(K)` javadoc contains stray `*` characters.
4. **Stale doc references** — `KeyedCache` javadoc mentions a non-existent `BasicCache`.
5. **README drift** — README documents a `Cache`/`CacheBuilder`/`CacheRegistry` API absent from
   `main`. Not touched by this design; flagged for a separate docs pass.

---

## 10. Handoff packet

```yaml
handoff:
  objective: Replace inheritance-based single-shape cache beans with composable shape components so one bean can expose multiple cache shapes at once.
  from: architecture-engineer
  to: task-planner
  status: complete
  risk: MEDIUM
  inputs:
    - src/main/java/com/bbl/cache/registry/*.java (current shapes + base + read contract)
    - src/main/java/com/bbl/cache/support/CacheFactory.java (pure builders, reused as-is)
    - src/main/java/com/bbl/cache/example/*.java (beans to migrate)
  constraints:
    - Do NOT modify CacheFactory or CacheUtils (immutability + dup-key behavior must stay identical).
    - Do NOT re-introduce forced inheritance on usage beans.
    - Do NOT change the KeyedCache read contract.
    - Do NOT make shape components or CacheGroup CDI beans (plain POJOs owned by the bean).
    - Do NOT resolve cross-view read atomicity silently — it is an accepted skew (4.2); escalate if a consumer needs strict atomicity.
  produced_artifacts:
    - path: docs/cache-registry-design.md
      description: This spec — schemas, interfaces, module boundaries, atomicity semantics, migration sketches, per-component verification hooks.
  definition_of_done: backend-developer can implement AbstractMapCache (sealed), the three final shape components, CacheGroup, and the migrated + new example beans with no further architectural judgment call; tester can assert every §8 hook including failure atomicity.
  notes: >
    Deliberate change flagged in 3.2 (log label source). Field-init order is a hard constraint (§5).
    Five pre-existing issues surfaced in §9 (broken imports block compilation and must be fixed during
    implementation). api-design not involved — no external/HTTP boundary in scope.
```
