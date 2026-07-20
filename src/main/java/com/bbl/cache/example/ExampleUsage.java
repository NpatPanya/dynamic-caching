package com.bbl.cache.example;

import com.bbl.cache.Cache;
import com.bbl.cache.CacheBuilder;
import com.bbl.cache.CacheConfigurationException;
import com.bbl.cache.CacheMissException;
import com.bbl.cache.registry.CacheRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Narrated, runnable examples of the public API, one scenario per method. Lives in test sources
 * (not shipped in the distributed jar) but is still compiled — and exercised via
 * {@code ExampleUsageTest} — on every {@code mvn test}, so it can't silently drift out of date.
 *
 * <p>Run standalone with: {@code mvn -q test-compile exec:java -Dexec.mainClass=com.bbl.cache.example.ExampleUsage
 * -Dexec.classpathScope=test} (requires exec-maven-plugin), or just invoke {@link #main} from an
 * IDE with the test classpath.
 */
@Deprecated
public final class ExampleUsage {

    private static final List<ServiceProvider> ALL_SERVICE_PROVIDERS = List.of(
            new ServiceProvider(new ServiceProviderId("id1", "serviceName1"), "providerName1", "onlinePayment"),
            new ServiceProvider(new ServiceProviderId("id2", "serviceName2"), "providerName2", "onlinePayment"),
            new ServiceProvider(new ServiceProviderId("id3", "serviceName3"), "providerName3", "onlinePayment"),
            new ServiceProvider(new ServiceProviderId("id4", "serviceName4"), "providerName4", "onlinePayment"),
            new ServiceProvider(new ServiceProviderId("id1", "serviceName1"), "providerName1", "mobileBanking"),
            new ServiceProvider(new ServiceProviderId("id2", "serviceName2"), "providerName2", "mobileBanking"),
            new ServiceProvider(new ServiceProviderId("id3", "serviceName3"), "providerName3", "mobileBanking"),
            new ServiceProvider(new ServiceProviderId("id4", "serviceName4"), "providerName4", "mobileBanking"));

    private ExampleUsage() {
    }

    public static void main(String[] args) {
        basicDtoCacheWithLambdaKey();
        plainPojoCacheWithManualPut();
        entityCacheKeyedByFieldName();
        sameEntityType_differentKeyFields();
        embeddedCompositeKeyField();
        fetchFromRepositoryThenLoadLater();
        reloadReplacesStaleData();
        cachingWholeListsAsASingleValue();
        namedCachesViaRegistry();
        listValuedCachesViaRegistry();
        categorizedCachesByServiceApp();
        usingTheCacheAsAPlainMap();
        readThroughWithFallbackDefault();
        handlingMissesAndConfigurationErrors();
        System.out.println("All ExampleUsage scenarios completed successfully.");
    }

    /** Scenario: the default way to key a cache — a lambda/method reference, resolved once per load. */
    private static void basicDtoCacheWithLambdaKey() {
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")))
                .buildAndLoad();

        require(users.size() == 2, "expected 2 users");
        require(users.getOrThrow("1").email().equals("a@example.com"), "unexpected email for id=1");
        System.out.println("basicDtoCacheWithLambdaKey: " + users.asMap());
    }

    /** Scenario: no loader at all — a cache can be populated one entry at a time via put(). */
    private static void plainPojoCacheWithManualPut() {
        Cache<PlainPojo> widgets = CacheBuilder.<PlainPojo>newBuilder().build();
        PlainPojo widget = new PlainPojo("p1", "widget");

        widgets.put(widget.getId(), widget);

        require(widgets.get("p1").isPresent(), "expected widget p1 to be cached");
        System.out.println("plainPojoCacheWithManualPut: " + widgets.asMap());
    }

    /** Scenario: withKeyField(...) reads a getter via reflection instead of a hand-written lambda. */
    private static void entityCacheKeyedByFieldName() {
        List<OrderEntity> orders = List.of(new OrderEntity("o1", "c1"), new OrderEntity("o2", "c2"));

        Cache<OrderEntity> byCustomer = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("customerId")
                .withLoader(() -> orders)
                .buildAndLoad();

        require(byCustomer.size() == orders.size(), "expected one entry per order");
        require(byCustomer.getOrThrow("c2").getOrderId().equals("o2"), "unexpected order for customerId=c2");
        System.out.println("entityCacheKeyedByFieldName: " + byCustomer.asMap());
    }

    /** Scenario: the same source list, keyed two different ways by naming a different field. */
    private static void sameEntityType_differentKeyFields() {
        List<OrderEntity> orders = List.of(new OrderEntity("o1", "c1"), new OrderEntity("o2", "c2"));

        Cache<OrderEntity> byOrderId = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("orderId")
                .withLoader(() -> orders)
                .buildAndLoad();
        Cache<OrderEntity> byCustomerId = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("customerId")
                .withLoader(() -> orders)
                .buildAndLoad();

        require(byOrderId.containsKey("o1") && byCustomerId.containsKey("c1"), "expected both keyings to succeed");
        System.out.println("sameEntityType_differentKeyFields: byOrderId=" + byOrderId.asMap().keySet()
                + ", byCustomerId=" + byCustomerId.asMap().keySet());
    }

    /**
     * Scenario: a JPA {@code @EmbeddedId}-style composite key. "referenceId" isn't a direct field
     * on {@code TransactionEntity} — it's nested inside its {@code TransactionId txnId} field —
     * but withKeyField finds it without needing a dotted path.
     */
    private static void embeddedCompositeKeyField() {
        List<TransactionEntity> transactions = List.of(
                new TransactionEntity(new TransactionId("PENDING", "ref-001"), "first payment"),
                new TransactionEntity(new TransactionId("SETTLED", "ref-002"), "second payment"));

        Cache<TransactionEntity> byReferenceId = CacheBuilder.<TransactionEntity>newBuilder()
                .withKeyField("referenceId")
                .withLoader(() -> transactions)
                .buildAndLoad();

        require(byReferenceId.size() == 2, "expected 2 transactions");
        require(byReferenceId.getOrThrow("ref-001").getTxnDetails().equals("first payment"),
                "unexpected details for ref-001");
        System.out.println("embeddedCompositeKeyField: " + byReferenceId.asMap().keySet());
    }

    /**
     * Scenario: the loader doesn't have to BE the database call — a repository fetch can happen
     * first, its result held in a plain variable, and the cache populated afterward as a
     * completely separate, later step.
     */
    private static void fetchFromRepositoryThenLoadLater() {
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder().build();
        require(users.isEmpty(), "expected an empty cache before any load");

        List<UserDto> fetchedFromDatabase = fakeRepositoryFindAll();

        users.load(() -> fetchedFromDatabase, UserDto::id);

        require(users.size() == fetchedFromDatabase.size(), "expected cache to match fetched data");
        System.out.println("fetchFromRepositoryThenLoadLater: " + users.asMap());
    }

    private static List<UserDto> fakeRepositoryFindAll() {
        return List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com"));
    }

    /** Scenario: reload() clears stale entries before repopulating from a fresh dataset. */
    private static void reloadReplacesStaleData() {
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")))
                .buildAndLoad();
        require(users.size() == 2, "expected 2 users before reload");

        users.reload(() -> List.of(new UserDto("2", "b2@example.com")), UserDto::id);

        require(users.size() == 1, "expected 1 user after reload");
        require(!users.containsKey("1"), "expected stale user 1 to be gone after reload");
        System.out.println("reloadReplacesStaleData: " + users.asMap());
    }

    /** Scenario: a whole List<T> cached as a single value under one key — Cache<V> has no constraint on V. */
    private static void cachingWholeListsAsASingleValue() {
        Cache<List<UserDto>> userLists = CacheBuilder.<List<UserDto>>newBuilder().build();
        List<UserDto> activeUsers = List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com"));

        userLists.put("active", activeUsers);

        require(userLists.getOrThrow("active").size() == 2, "expected 2 users in the 'active' list");
        System.out.println("cachingWholeListsAsASingleValue: " + userLists.asMap());
    }

    /** Scenario: bootstrapping several independently-typed caches and looking them up by name. */
    private static void namedCachesViaRegistry() {
        CacheRegistry registry = CacheRegistry.create();
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com")))
                .buildAndLoad();
        Cache<OrderEntity> orders = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("orderId")
                .withLoader(() -> List.of(new OrderEntity("o1", "c1")))
                .buildAndLoad();

        registry.register("users", users, UserDto.class);
        registry.register("orders", orders, OrderEntity.class);

        require(registry.get("users", UserDto.class) == users, "expected the same users cache back");
        System.out.println("namedCachesViaRegistry: users=" + registry.get("users", UserDto.class).asMap()
                + ", orders=" + registry.get("orders", OrderEntity.class).asMap());
    }

    /** Scenario: registering a Cache<List<E>> through the registry, which needs its own methods. */
    private static void listValuedCachesViaRegistry() {
        CacheRegistry registry = CacheRegistry.create();
        Cache<List<UserDto>> userLists = CacheBuilder.<List<UserDto>>newBuilder().build();
        userLists.put("active", List.of(new UserDto("1", "a@example.com")));

        registry.registerList("userLists", userLists, UserDto.class);

        Cache<List<UserDto>> retrieved = registry.getList("userLists", UserDto.class);
        require(retrieved == userLists, "expected the same list-valued cache back");
        System.out.println("listValuedCachesViaRegistry: " + retrieved.asMap());
    }

    /**
     * Real-life scenario: partition a raw dataset by a category field (here, {@code serviceApp}),
     * cache each segment independently, and keep the resulting caches in a plain
     * {@code Map<String, Cache<ServiceProvider>>} for lookup by category. Independent per-segment
     * caches mean the same {@code providerId} can safely appear in more than one segment — each
     * cache only ever sees its own slice of the data, so there's no cross-segment key collision.
     */
    private static void categorizedCachesByServiceApp() {
        Map<String, List<ServiceProvider>> providersBySegment = new HashMap<>();
        for (ServiceProvider provider : ALL_SERVICE_PROVIDERS) {
            providersBySegment.computeIfAbsent(provider.getServiceApp(), key -> new ArrayList<>()).add(provider);
        }

        Map<String, Cache<ServiceProvider>> cachesByCategory = new HashMap<>();
        for (Map.Entry<String, List<ServiceProvider>> segment : providersBySegment.entrySet()) {
            Cache<ServiceProvider> cache = CacheBuilder.<ServiceProvider>newBuilder()
                    .withKeyField("providerId") // embedded inside ServiceProvider.id.providerId
                    .withLoader(segment::getValue)
                    .buildAndLoad();
            cachesByCategory.put(segment.getKey(), cache);
        }

        Cache<ServiceProvider> mobileBanking = cachesByCategory.get("mobileBanking");
        require(mobileBanking.size() == 4, "expected 4 mobileBanking providers");
        require(mobileBanking.getOrThrow("id2").getProviderName().equals("providerName2"),
                "unexpected provider for id2 in the mobileBanking segment");
        System.out.println("categorizedCachesByServiceApp: mobileBanking=" + mobileBanking.asMap().keySet()
                + ", onlinePayment=" + cachesByCategory.get("onlinePayment").asMap().keySet());
    }

    /**
     * Scenario: once populated, {@link Cache#asMap()} behaves exactly like any other Java
     * {@code Map} — iterate {@code entrySet()}, use {@code keySet()}/{@code values()}, stream and
     * filter it. The one difference from a plain {@code HashMap} is that it's unmodifiable and
     * live: mutations must go through the {@link Cache} API ({@code put}/{@code remove}/{@code
     * clear}), and once you do, the map view reflects the change immediately.
     */
    private static void usingTheCacheAsAPlainMap() {
        Cache<OrderEntity> orders = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("orderId")
                .withLoader(() -> List.of(
                        new OrderEntity("o1", "c1"), new OrderEntity("o2", "c2"), new OrderEntity("o3", "c1")))
                .buildAndLoad();

        Map<String, OrderEntity> asMap = orders.asMap();

        // Iterate like any other Map
        for (Map.Entry<String, OrderEntity> entry : asMap.entrySet()) {
            System.out.println("usingTheCacheAsAPlainMap: order " + entry.getKey()
                    + " -> customer " + entry.getValue().getCustomerId());
        }

        // keySet() / values() are standard Map operations, no cache-specific API needed
        require(asMap.containsKey("o1"), "expected o1 in keySet()");
        require(asMap.values().stream().anyMatch(o -> o.getCustomerId().equals("c1")),
                "expected a c1 order in values()");

        // Stream + filter, exactly like any Map — e.g. every order placed by customer "c1"
        List<String> customerC1Orders = asMap.entrySet().stream()
                .filter(entry -> entry.getValue().getCustomerId().equals("c1"))
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
        require(customerC1Orders.equals(List.of("o1", "o3")), "expected orders o1 and o3 for customer c1");

        // It's a live but unmodifiable view — mutate through the Cache, not the map
        try {
            asMap.put("o4", new OrderEntity("o4", "c9"));
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            System.out.println("usingTheCacheAsAPlainMap: confirmed asMap() rejects direct writes"
                    + " - use cache.put(...) instead");
        }

        orders.put("o4", new OrderEntity("o4", "c9"));
        require(asMap.containsKey("o4"), "expected the live view to reflect the cache.put() that just happened");
        System.out.println("usingTheCacheAsAPlainMap: customerC1Orders=" + customerC1Orders + ", finalMap=" + asMap);
    }

    /** Scenario: a common read-through pattern — fall back to a default when a key isn't cached. */
    private static void readThroughWithFallbackDefault() {
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com")))
                .buildAndLoad();
        UserDto guest = new UserDto("guest", "guest@example.com");

        UserDto resolved = users.get("does-not-exist").orElse(guest);

        require(resolved == guest, "expected fallback to the guest user for an uncached id");
        System.out.println("readThroughWithFallbackDefault: " + resolved);
    }

    /** Scenario: the two exceptions you're expected to handle — a missing key, and a bad withKeyField. */
    private static void handlingMissesAndConfigurationErrors() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();

        try {
            cache.getOrThrow("missing");
            throw new AssertionError("expected CacheMissException");
        } catch (CacheMissException expected) {
            System.out.println("handlingMissesAndConfigurationErrors: caught expected " + expected.getMessage());
        }

        try {
            CacheBuilder.<OrderEntity>newBuilder()
                    .withKeyField("thisFieldDoesNotExistAnywhere")
                    .withLoader(() -> List.of(new OrderEntity("o1", "c1")))
                    .buildAndLoad();
            throw new AssertionError("expected CacheConfigurationException");
        } catch (CacheConfigurationException expected) {
            System.out.println("handlingMissesAndConfigurationErrors: caught expected " + expected.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("ExampleUsage assertion failed: " + message);
        }
    }
}
