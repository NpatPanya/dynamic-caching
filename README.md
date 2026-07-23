# dynamic-caching

A Java 17 library for registering immutable in-memory data snapshots under simple string names. A registry entry may contain a domain object, list, map, or nested map and can optionally expire after a TTL.

## Requirements

- Java 17+
- Maven 3.8+

## Build

```bash
mvn test
mvn install
```

The artifact coordinates are `com.bbl.cache:dynamic-caching:1.0-SNAPSHOT`.

## String-keyed registry

Use stable string constants and declare the expected return type strictly:

```java
private static final String SETTINGS = "settings";

CacheRegistry registry = CacheRegistry.getInstance();
registry.register(SETTINGS, Map.of("theme", "dark"));

Map<String, String> settings = registry.get(SETTINGS);
String theme = settings.get("theme");
```

The assignment type tells Java what `get` should return. No caller cast is needed. Type correctness is the caller's responsibility: declaring a wrong raw type throws `ClassCastException`. Java type erasure means a wrong nested generic type may fail only when an element is read.

Registry lifecycle:

```java
registry.register("customers", initialCustomers);

Map<Long, Customer> current = registry.get("customers");
Optional<Map<Long, Customer>> optional = registry.find("customers");

registry.reregister("customers", refreshedCustomers);
registry.unregister("customers");
registry.invalidateAll();
```

- `register` rejects duplicate names.
- `get` throws `NoSuchElementException` when the name is missing or expired.
- `find` returns `Optional.empty()` when missing or expired.
- `reregister` atomically replaces the existing snapshot under the same name. It may replace it with another type and starts a fresh TTL when supplied.
- `unregister` removes one name.
- `invalidateAll` removes every registration without mutating snapshots already returned to callers.

List, set, map, general collection, nested collection, and array containers are copied recursively. Collections are normalized to immutable `List`, `Set`, `Map`, or `Collection` views, so use those interface types for strict assignment; a concrete expectation such as `ArrayList` is a different raw type and may throw `ClassCastException`. Arrays are defensively copied on every read, so mutating a returned array never changes the stored snapshot. Custom domain objects inside a snapshot remain the same references, so immutable records or classes are recommended.

## Filtering and shaping data

`DataFilter` filters source collections and selects the output shape. Bean fields use method references and conditions use predicates:

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

Each map method also has a `valueExtractor` overload. Results are immutable. Duplicate keys and duplicate nested key pairs throw `IllegalStateException`; null extractor results are rejected.

## TTL and refresh

TTL belongs to one complete registered snapshot:

```java
registry.register("active-customers", activeById, Duration.ofMinutes(10));
```

After expiration, `find` returns empty and `get` throws `NoSuchElementException`. There is no automatic reload. Publish a replacement and start a fresh TTL with:

```java
registry.reregister(
        "active-customers",
        refreshed,
        Duration.ofMinutes(10));
```

Registration and replacement are atomic. Concurrent readers see either the old immutable snapshot or the new one. References obtained before replacement remain valid.

Runnable examples are in `src/main/java/com/bbl/cache/examples/`.

## Testing

```bash
mvn test
```

## License

Not yet decided.