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
        .withKeyExtractor(User::getId)          // you decide the key — no reflection, no conventions
        .withLoader(userRepository::findAll)    // called once, synchronously, whenever you trigger load
        .buildAndLoad();

users.get("123");        // Optional<User>
users.getOrThrow("123"); // User, or throws CacheMissException
users.reload(() -> userRepository.findAll(), User::getId); // clear + repopulate
```

Cached values can be anything — a plain object, an immutable `record` DTO, a JPA entity. The
library never inspects the object; you always supply the key via a `KeyExtractor<T>`.

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
