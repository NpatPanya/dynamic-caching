# String-Key Cache Registry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make string names the primary registry API while retaining deprecated `RegistryKey` and cache-wrapper migration paths.

**Architecture:** Extract immutable snapshot logic into a package-private helper shared by the new string path and deprecated typed-key path. Store raw snapshots in the existing concurrent registry entry, infer retrieval type from the caller, and isolate the unavoidable unchecked cast.

**Tech Stack:** Java 17, JUnit 5, Maven, `ConcurrentHashMap`.

## Global Constraints

- Preserve defensive copying for collection and array containers.
- `reregister` atomically replaces the current value and resets TTL.
- `invalidateAll` removes registrations but does not mutate snapshots already returned.
- Keep `.mcp.json` untracked and untouched.

---

### Task 1: String-Based Registry Lifecycle

**Files:**
- Create: `src/main/java/com/bbl/cache/registry/SnapshotSupport.java`
- Modify: `src/main/java/com/bbl/cache/registry/CacheRegistry.java`
- Modify: `src/main/java/com/bbl/cache/registry/RegistryKey.java`
- Test: `src/test/java/com/bbl/cache/registry/StringKeyCacheRegistryTest.java`

**Interfaces:**
- Produces: `<T> T register(String,T)`, `<T> T reregister(String,T)`, `<T> T get(String)`, `<T> Optional<T> find(String)`, `invalidateAll()`.
- Preserves: deprecated `RegistryKey<T>` overloads through shared snapshot support.

- [ ] **Step 1: Write failing lifecycle tests**

```java
Map<String, String> stored = registry.register("settings", Map.of("theme", "dark"));
Map<String, String> retrieved = registry.get("settings");
assertEquals("dark", retrieved.get("theme"));
assertThrows(NoSuchElementException.class, () -> registry.get("missing"));
assertEquals(Optional.empty(), registry.find("missing"));
```

- [ ] **Step 2: Verify lifecycle tests fail**

Run: `mvn '-Dtest=StringKeyCacheRegistryTest' test`

Expected: compilation failure because the string raw-data overloads do not exist.

- [ ] **Step 3: Implement snapshot support and string lifecycle**

Move recursive freeze, array detection, and defensive exposure into
`SnapshotSupport`. Store raw data, registration time, TTL, and copy-on-read
metadata in a `ValueEntry`. Use `putIfAbsent` for `register`, `compute` for
`reregister`, and compare-and-remove for expiration.

- [ ] **Step 4: Verify lifecycle tests pass**

Run: `mvn '-Dtest=StringKeyCacheRegistryTest' test`

Expected: all lifecycle tests pass.

### Task 2: Replacement, TTL, Type Failure, and Invalidation

**Files:**
- Modify: `src/test/java/com/bbl/cache/registry/StringKeyCacheRegistryTest.java`
- Modify: `src/main/java/com/bbl/cache/registry/CacheRegistry.java`

**Interfaces:**
- Consumes: Task 1 string lifecycle.
- Produces: atomic cross-type replacement, TTL refresh, wrong-type runtime failure, and `invalidateAll`.

- [ ] **Step 1: Add failing behavior tests**

```java
registry.register("value", "old");
registry.reregister("value", List.of("new"));
List<String> replacement = registry.get("value");
assertEquals(List.of("new"), replacement);

registry.invalidateAll();
assertEquals(Optional.empty(), registry.find("value"));
assertThrows(NoSuchElementException.class, () -> registry.get("value"));
```

Also test strict TTL boundaries, fresh TTL after `reregister`, concurrent
registration with one winner, defensive array reads, and a wrong raw assignment
throwing `ClassCastException`.

- [ ] **Step 2: Verify new tests fail**

Run: `mvn '-Dtest=StringKeyCacheRegistryTest' test`

Expected: failures for unimplemented replacement or invalidation behavior.

- [ ] **Step 3: Implement minimal behavior**

Ensure `reregister` replaces any string-keyed type atomically, `invalidateAll`
delegates to the registry map's `clear`, and deprecated `clear()` delegates to
`invalidateAll`.

- [ ] **Step 4: Verify behavior tests pass**

Run: `mvn '-Dtest=StringKeyCacheRegistryTest' test`

Expected: all string registry tests pass.

### Task 3: Deprecation and Migration

**Files:**
- Modify: `src/main/java/com/bbl/cache/registry/RegistryKey.java`
- Modify: `src/main/java/com/bbl/cache/registry/CacheRegistry.java`
- Modify: `src/main/java/com/bbl/cache/examples/*.java`
- Modify: `src/test/java/com/bbl/cache/registry/CacheRegistryV2Test.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: Task 2 string API.
- Produces: deprecated typed-key path and renamed `getCache(String)` legacy adapter.

- [ ] **Step 1: Add compatibility compilation tests**

```java
Cache<String, String> legacy =
        registry.<String, String>getCache("legacy").orElseThrow();
```

Keep key-based tests compiling under deprecation suppression.

- [ ] **Step 2: Verify compatibility tests fail**

Run: `mvn '-Dtest=CacheRegistryV2Test,RegistryKeyTest' test`

Expected: compilation failure until `getCache` exists and signatures migrate.

- [ ] **Step 3: Deprecate and migrate**

Mark `RegistryKey` and all key-based overloads deprecated. Rename legacy
cache-wrapper `get(String)` to `getCache(String)`. Update examples and README to
string constants, direct typed assignment, `find`, `reregister`, and
`invalidateAll`.

- [ ] **Step 4: Run complete verification**

Run: `mvn test`

Expected: all tests pass with zero failures.

Run: `git diff --check`

Expected: no output and exit code 0.
