# dynamic-caching

A minimal, zero-dependency Java library for caching data at application startup — works the same
whether you're on Spring, Jakarta EE, Micronaut, Quarkus, or plain Java SE.

The core library has **no framework dependency**. You decide how objects get loaded and how they
get keyed; the library just holds them in a thread-safe, `String`-keyed cache.

## Requirements

- Java 17+
- No runtime dependencies

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

## Usage

```java
Cache<User> users = CacheBuilder.<User>newBuilder()
        .withKeyExtractor(User::getId)          // you decide the key
        .withLoader(userRepository::findAll)    // called once, synchronously, whenever you trigger load
        .buildAndLoad();

users.get("123");        // Optional<User>
users.getOrThrow("123"); // User, or throws CacheMissException
users.reload(() -> userRepository.findAll(), User::getId); // clear + repopulate
```

Cached values can be anything — a plain object, an immutable `record` DTO, a JPA entity. By
default you supply the key via a `KeyExtractor<T>` lambda; see below for a reflection-based
alternative.

### Keying by field name instead of a lambda

`withKeyField(String)` reads a named field/getter off each object via reflection, for cases where
the key field is config-driven rather than known at the call site:

```java
Cache<OrderEntity> byCustomer = CacheBuilder.<OrderEntity>newBuilder()
        .withKeyField("customerId")             // or "orderId" — whichever field you name
        .withLoader(orderRepository::findAll)
        .buildAndLoad();
```

Given a `List<OrderEntity>` of 5 elements, this produces a cache of size 5 — one entry per
element, keyed by that element's `customerId`. Resolution order per object: a no-arg method named
exactly `fieldName` (matches record components), then `getFieldName`/`isFieldName` (JavaBean
getters), then direct field access as a fallback (works even with no public getter).

If `fieldName` isn't found directly on the object, its own non-simple fields are searched the
same way, recursively — so a JPA `@EmbeddedId`-style composite key still works without a dotted
path:

```java
class TransactionId { private String referenceId; /* ... */ }
class TransactionEntity { private TransactionId txnId; private String txnDetails; /* ... */ }

Cache<TransactionEntity> cache = CacheBuilder.<TransactionEntity>newBuilder()
        .withKeyField("referenceId")   // not a direct field on TransactionEntity — found inside txnId
        .withLoader(() -> transactions)
        .buildAndLoad();
```

Throws `CacheConfigurationException` if the field can't be resolved anywhere in the object graph.

### Triggering the load

`CacheBuilder` doesn't know or care about your framework's startup lifecycle. Call
`buildAndLoad()` (or `build()` followed by `cache.load(...)`) from whichever hook your framework
gives you:

- Spring: `@PostConstruct` or an `ApplicationRunner` bean
- Jakarta EE / servlets: `ServletContextListener#contextInitialized`
- Micronaut / Quarkus: a startup event listener
- Plain Java SE: the first line of `main()`

### Multiple caches

For apps that bootstrap several independently-typed caches and look them up by name, use
`CacheRegistry` instead of hand-rolling a `Map<String, Cache<?>>`:

```java
CacheRegistry registry = CacheRegistry.create();
registry.register("users", usersCache, User.class);
registry.register("orders", ordersCache, Order.class);

Cache<User> users = registry.get("users", User.class); // throws if name/type mismatch
```

A single cache used at one call site doesn't need the registry — just hold the `Cache<V>` instance
directly.

### Caching whole lists

`Cache<V>` places no constraint on `V` — it's already fine to cache a whole `List<T>` as a single
value under one key:

```java
Cache<List<UserDto>> userLists = CacheBuilder.<List<UserDto>>newBuilder().build();
userLists.put("active", userRepository.findAllActive());
```

To register/retrieve a list-valued cache through `CacheRegistry`, use `registerList`/`getList`
instead of `register`/`get`. A separate pair of methods exists because `Class<List<UserDto>>`
can't be expressed directly under Java's type erasure — these take the list's *element* type
instead, so no unchecked cast is needed on your side:

```java
registry.registerList("userLists", userLists, UserDto.class);
Cache<List<UserDto>> retrieved = registry.getList("userLists", UserDto.class);
```

## Full worked examples

`src/test/java/com/bbl/cache/example/ExampleUsage.java` narrates every scenario above as runnable
code, and is exercised by `mvn test` on every build so it can't drift out of date. Read it
top-to-bottom for a guided tour, or copy individual methods as a starting point. Beyond the basics,
it covers:

- **`categorizedCachesByServiceApp`** — a realistic segmentation pattern: partition a raw dataset
  by a category field, cache each segment independently, and keep the resulting caches in a plain
  `Map<String, Cache<V>>` for lookup by category.
- **`usingTheCacheAsAPlainMap`** — once populated, `asMap()` behaves like any other `Map`:
  `entrySet()` iteration, `keySet()`/`values()`, streaming/filtering — it's just unmodifiable
  (mutate through the `Cache` API, not the map view), and it's a *live* view of the cache.
- **`readThroughWithFallbackDefault`** — falling back to a default value via `get(key).orElse(...)`
  when a key isn't cached.
- **`handlingMissesAndConfigurationErrors`** — the two exceptions to expect and catch.

## Design notes

- Backed by `ConcurrentHashMap` — safe for the startup write burst followed by concurrent reads.
- `reload()` does a simple clear-then-repopulate; readers may transiently see fewer entries during
  a reload. Acceptable for the startup-cache use case this library targets.
- Out of scope for v1: TTL/eviction, async loading, framework-specific auto-loader modules.

## Testing

```bash
mvn test
```

## License

Not yet decided.
