# String-Key Cache Registry Design

## Summary

Make string names the primary registry key so callers can register arbitrary
data and retrieve it through a strictly declared assignment type:

```java
registry.register("stringMap", Map.of("theme", "dark"));
Map<String, String> stringMap = registry.get("stringMap");
```

`RegistryKey<T>` and its registry overloads remain functional but become
deprecated migration APIs.

## Public API

The primary `CacheRegistry` surface is:

```java
public <T> T register(String name, T data);
public <T> T register(String name, T data, Duration ttl);
public <T> T reregister(String name, T data);
public <T> T reregister(String name, T data, Duration ttl);
public <T> T get(String name);
public <T> Optional<T> find(String name);
public boolean unregister(String name);
public void invalidateAll();
```

- `register` rejects blank names, null data, invalid TTLs, and duplicate names.
- `reregister` atomically creates or replaces any named value. The replacement
  becomes the current snapshot for that name and starts a fresh TTL when one is
  supplied.
- `get` throws `NoSuchElementException` when the name is absent or expired.
- `find` returns `Optional.empty()` when the name is absent or expired.
- Collection and array containers retain defensive snapshot and copy-on-read
  behavior.
- Expired entries are removed with compare-and-remove so a concurrent
  replacement is never deleted.
- `invalidateAll` removes every current registration. Previously returned
  immutable snapshots remain valid references, and removed names can be
  registered again immediately.
- Existing `clear()` becomes a deprecated alias for `invalidateAll()`.

## Type Contract and Compatibility

`get(String)` uses the assignment target to infer `T`. Type correctness is the
caller's responsibility. A wrong raw type throws `ClassCastException` at the
assignment or method-use boundary. Java erasure means a wrong nested generic
type can fail later when an element is read; the registry cannot validate it
without an explicit type token.

The implementation isolates the required unchecked cast in one internal
method. Suppressing that compiler warning does not suppress runtime exceptions.

`RegistryKey<T>` and all key-based registry overloads are marked deprecated but
remain tested and functional. The old deprecated cache-wrapper `get(String)`
is renamed to `getCache(String)` because Java cannot overload methods solely
by return type. Its other overloads remain available where their signatures do
not conflict.

Snapshot behavior moves from `RegistryKey` into a package-private helper so
string and deprecated typed-key paths share one implementation.

## Documentation and Tests

- Migrate all runnable examples and README snippets to string constants and
  direct typed assignment.
- Test scalar, object, list, map, nested-map, and array registration.
- Test wrong raw types, missing/expired `get`, optional `find`, duplicate
  registration races, atomic replacement, TTL refresh, defensive snapshots,
  `invalidateAll`, and concurrent expiry/replacement.
- Preserve tests for deprecated `RegistryKey` and cache-wrapper adapters.
- Acceptance requires `mvn test` and `git diff --check` to pass.
