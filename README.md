# dynamic-caching

A small Java library for holding startup-loaded reference data in memory as immutable,
thread-safe map snapshots — no TTL, no eviction, no async loading. You load a collection once
(typically at application startup) and read from it concurrently afterward.

## Requirements

- Java 17+
- `org.apache.logging.log4j:log4j-core` (each cache logs through an injected `Logger`)
- `jakarta.enterprise.cdi-api` (example beans are CDI-managed; the core library itself has no CDI dependency)

## Install

Not yet published to a repository. Build locally with Maven:

```bash
mvn install
```

```xml
<dependency>
    <groupId>com.bbl.cache</groupId>
    <artifactId>dynamic-caching</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Core concepts

The library models three cache "shapes", all built on the same immutable-snapshot approach:

| Shape | Structure | Use case |
|---|---|---|
| `UniqueCache<K, V>` | `Map<K, V>` | one value per key — reference data |
| `GroupedCache<K, V>` | `Map<K, List<V>>` | one-to-many — all values sharing a key |
| `DoubleKeyCache<K1, K2, V>` | `Map<K1, Map<K2, V>>` | two-level lookup, e.g. group then unique key within group |

Each shape is `final` (not meant to be subclassed) and is constructed through `CacheFacade`, which
lets a single application bean hold several cache shapes side by side under one registry instead of
forcing inheritance from a single base cache class:

```java
private final CacheFacade caches = new CacheFacade("ProviderCatalogCache", log);

private final UniqueCache<String, Provider> byId = caches.uniqueCache("byId");
private final GroupedCache<String, Provider> byCountry = caches.groupedCache("byCountry");
private final DoubleKeyCache<String, String, Provider> byServiceProvider =
        caches.doubleKeyCache("byServiceProvider");
```

`CacheFacade` enforces unique component names within the bean and provides cross-cutting
operations across all of a bean's caches: `clearAll()`, `sizes()`, `totalSize()`, `names()`.

### Loading data: stage, then publish

Each cache separates *building* a snapshot from *publishing* it:

- `stage(collection, keyExtractor[, valueExtractor])` builds and returns a new immutable snapshot
  without touching the live cache. Throws `IllegalStateException` on a duplicate key.
- `publish(snapshot)` atomically swaps the live snapshot in with a single volatile write. Cannot throw.
- `load(...)` is a convenience for `publish(stage(...))` — build and publish in one call.

Separating the two matters when a bean owns multiple cache views that must refresh together:
stage all of them first (so any duplicate-key failure aborts before anything is published), then
publish all of them, so no view is ever left half-updated relative to the others:

```java
public void refresh(List<String> serviceNames) {
    List<Provider> src = List.copyOf(repo.findByServiceNames(serviceNames)); // materialize once

    var idMap = byId.stage(src, Provider::getId);
    var countryMap = byCountry.stage(src, Provider::getCountry);
    var svcProvMap = byServiceProvider.stage(src, Provider::getServiceName, Provider::getId);

    byId.publish(idMap);
    byCountry.publish(countryMap);
    byServiceProvider.publish(svcProvMap);
}
```

If the source and cached value types differ, pass a `valueExtractor` alongside the key
extractor(s) — every shape has an overload for this:

```java
GroupedCache<String, String> countryToServiceNames = caches.groupedCache("countryToServiceNames");
countryToServiceNames.load(providers, Provider::getCountry, Provider::getServiceName);
```

### Reading

Reads go directly through the typed field, not through `CacheFacade`:

```java
Provider p = byId.get("p-1");                  // null if absent
List<Provider> inUs = byCountry.get("US");      // empty list if absent, never null
Provider match = byServiceProvider.get("svc", "p-1");
```

`getOrDefault(key, def)` and `containsKey(key)` are also available on every shape.

## Full worked examples

- `src/main/java/com/bbl/cache/example/ProviderCatalogCache.java` — a single bean holding all
  three shapes side by side, populated from one source fetch with failure-atomic refresh.
- `src/main/java/com/bbl/cache/example/CompositeDoubleKeyCacheUsage.java` — a `DoubleKeyCache`
  used on its own inside a bean.

These example files intentionally reference illustrative entity/repository types
(`src/main/java/com/bbl/cache/example/entity/`) that model a plausible caller, not the library's
own dependencies, and are excluded from the module's own build (see `pom.xml`'s
`maven-compiler-plugin` exclude and `docs/cache-registry-design.md`). Read them for the pattern,
don't expect them to compile standalone.

For runnable, test-verified usage, see `src/test/java/com/bbl/cache/registry/`
(`UniqueCacheTest`, `GroupedCacheTest`, `DoubleKeyCacheTest`, `CacheFacadeTest`,
`MultiShapeCacheTest`).

## Design notes

See `docs/cache-registry-design.md` for the full design rationale (why the shapes are `final`
and constructed via a facade rather than subclassed, why `stage`/`publish` are split, atomicity
guarantees, etc.).

- Backed by `Collectors.toUnmodifiableMap` / immutable `List.copyOf` snapshots — safe for the
  startup write burst followed by concurrent reads.
- `publish()` is a single `volatile` field write and cannot throw; a failed `stage()` never
  affects the live cache.
- Out of scope: TTL/eviction, async/background loading, partial/incremental updates.

## Testing

```bash
mvn test
```

## License

Not yet decided.
