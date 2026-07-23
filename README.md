# dynamic-caching

A Java 17 library for registering typed, immutable in-memory data snapshots. A registry entry may contain a domain object, list, map, or nested map and can optionally expire after a TTL.

## Requirements

- Java 17+
- Maven 3.8+

## Build

```bash
mvn test
mvn install
```

The artifact coordinates are `com.bbl.cache:dynamic-caching:1.0-SNAPSHOT`.

## Typed registry

Declare each registry key once as a shared constant. The key carries the complete compile-time data type, including generic collection parameters:

```java
private static final RegistryKey<Map<String, String>> SETTINGS =
        RegistryKey.map("settings", String.class, String.class);

CacheRegistry registry = CacheRegistry.getInstance();
registry.register(SETTINGS, Map.of("theme", "dark"));

String theme = registry.get(SETTINGS)
        .orElseThrow()
        .get("theme");
```

No cast is needed. Another `RegistryKey` instance cannot claim an occupied name, even when it declares the same Java type. Keep keys in shared constants and pass the same instance to `register`, `get`, `reregister`, and `unregister`.

Available key factories are:

- `RegistryKey.value(name, Type.class)` for a scalar or domain object
- `RegistryKey.list(name, Element.class)`
- `RegistryKey.set(name, Element.class)`
- `RegistryKey.map(name, Key.class, Value.class)`
- `RegistryKey.nestedMap(name, OuterKey.class, InnerKey.class, Value.class)`

List, set, map, nested collection, and array containers are copied recursively. Registered containers cannot be changed through the original source or returned value. Custom domain objects inside a snapshot remain the same references, so immutable records or classes are recommended.

`register` rejects duplicate names. `reregister` atomically creates or replaces data for the same key. Changing the type assigned to a name requires `unregister` followed by `register`.

## Filtering and shaping data

`DataFilter` is responsible for filtering source collections and selecting the output shape. Bean fields are expressed with type-safe method references and conditions with predicates:

```java
List<Customer> active =
        DataFilter.filterToList(customers, Customer::active);

Map<Long, Customer> activeById =
        DataFilter.filterToMap(
                customers, Customer::active, Customer::id);

Map<String, Map<Long, Customer>> activeByCountryAndId =
        DataFilter.filterToNestedMap(
                customers,
                Customer::active,
                Customer::country,
                Customer::id);
```

Each map method also has a `valueExtractor` overload when the cached value differs from the source object. Results are immutable. Duplicate keys and duplicate nested key pairs throw `IllegalStateException`; null extractor results are rejected.

Filtering stays separate from registration:

```java
private static final RegistryKey<Map<Long, Customer>> ACTIVE_CUSTOMERS =
        RegistryKey.map("active-customers", Long.class, Customer.class);

registry.register(
        ACTIVE_CUSTOMERS,
        DataFilter.filterToMap(customers, Customer::active, Customer::id));
```

## TTL and refresh

TTL belongs to one complete registered snapshot:

```java
registry.register(ACTIVE_CUSTOMERS, activeById, Duration.ofMinutes(10));
```

Before the boundary, `get` returns the snapshot. After the boundary, it returns `Optional.empty()` and lazily removes only that expired registration. There is no automatic reload; publish a fresh snapshot with `reregister`, which also starts a fresh TTL:

```java
registry.reregister(ACTIVE_CUSTOMERS, refreshed, Duration.ofMinutes(10));
```

Registration and replacement are atomic. Concurrent readers see either the old immutable snapshot or the new one.

## Migration from the wrapper API

`Cache`, `Caches`, `CacheDescriptor`, and `TtlCache`, together with the corresponding `CacheRegistry` overloads, are deprecated migration adapters. Existing callers remain functional, but new code should register raw data with `RegistryKey<T>` and use `DataFilter` for collection transformations.

Runnable examples are in `src/main/java/com/bbl/cache/examples/`.

## Testing

```bash
mvn test
```

## License

Not yet decided.