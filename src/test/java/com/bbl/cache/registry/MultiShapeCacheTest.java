package com.bbl.cache.registry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executed proof of the original requirement: "one object should be able to
 * use all cache types (unique-key, double-key, group-key) at once."
 *
 * <p>Previously impossible because the cache shapes were base classes a bean
 * had to {@code extend}, and Java only allows single inheritance. The
 * composition-based redesign ({@link CacheFacade}, {@link UniqueCache},
 * {@link GroupedCache}, {@link DoubleKeyCache}) fixes this by letting a bean
 * <em>hold</em> all three shapes as fields.
 *
 * <p>The design doc's illustrative example ({@code ProviderCatalogCache},
 * §6.4 of {@code docs/cache-registry-design.md}) references {@code Provider}
 * / {@code ProviderRepositoryPort} types that don't exist in this module, so
 * {@code com/bbl/cache/example/**} is excluded from compilation in
 * {@code pom.xml} and is never compile-checked. This test does not depend on
 * that package; instead it defines a local, compiled, executed fixture
 * ({@link ProviderCatalog}) that follows the exact same pattern described in
 * §5 and §6.4, and is therefore the real, executed proof that the
 * requirement is satisfied.
 *
 * <p><b>Field-init-ordering note:</b> §5 of the design doc mandates that the
 * {@code CacheGroup} field be declared/initialized <em>before</em> any field
 * that calls {@code caches.uniqueCache(...)} etc., because those factory
 * calls are instance-initializer expressions that dereference {@code caches}
 * and would NPE if {@code caches} were still {@code null} at that point (Java
 * initializes instance fields top-to-bottom in declaration order). The local
 * {@link ProviderCatalog} fixture below is built with {@code caches} declared
 * first, and its successful construction/use in every test in this class is
 * the executable proxy for that ordering constraint. Full CDI-startup
 * coverage of the real {@code ProviderCatalogCache} bean itself remains
 * blocked by its missing illustrative types and is tracked, not silently
 * dropped.
 */
class MultiShapeCacheTest {

    private static final Logger logger = LogManager.getLogger(MultiShapeCacheTest.class);

    /** Simple fixture record standing in for the design doc's {@code Provider}. */
    record Item(String id, String category, String bucket) {
    }

    /**
     * Local stand-in for the design doc's {@code ProviderCatalogCache} (§6.4).
     *
     * <p>Holds all three cache shapes side by side via one {@link CacheFacade},
     * with {@code caches} declared/initialized before any field that uses it
     * — the hard ordering constraint from §5.
     */
    static final class ProviderCatalog {

        // MUST be declared first: the shape fields below call caches.xCache(...)
        // as instance-initializer expressions, so caches must already be
        // constructed when those initializers run.
        private final CacheFacade caches = new CacheFacade("ProviderCatalog", logger);

        private final UniqueCache<String, Item> byId = caches.uniqueCache("byId");
        private final GroupedCache<String, Item> byCategory = caches.groupedCache("byCategory");
        private final DoubleKeyCache<String, String, Item> byCategoryAndBucket =
                caches.doubleKeyCache("byCategoryAndBucket");

        /** Failure-atomic refresh of all three views from one materialized fetch. */
        void refresh(List<Item> src) {
            // 1. build all snapshots first (any dup-key throws here, before any publish)
            Map<String, Item> idMap = byId.stage(src, Item::id);
            Map<String, List<Item>> categoryMap = byCategory.stage(src, Item::category);
            Map<String, Map<String, Item>> categoryBucketMap =
                    byCategoryAndBucket.stage(src, Item::category, Item::bucket);

            // 2. publish — cannot throw; all views advance together
            byId.publish(idMap);
            byCategory.publish(categoryMap);
            byCategoryAndBucket.publish(categoryBucketMap);
        }

        Item byId(String id) {
            return byId.get(id);
        }

        List<Item> byCategory(String category) {
            return byCategory.get(category);
        }

        Item byCategoryAndBucket(String category, String bucket) {
            return byCategoryAndBucket.get(category, bucket);
        }

        void clearAll() {
            caches.clearAll();
        }

        Map<String, Integer> sizes() {
            return caches.sizes();
        }
    }

    private static List<Item> sourceData() {
        return Arrays.asList(
                new Item("1", "fruit", "sweet"),
                new Item("2", "fruit", "citrus"),
                new Item("3", "veggie", "leafy"),
                new Item("4", "veggie", "root")
        );
    }

    /**
     * Direct, executed proof of the original requirement: one object exposes
     * unique-key, group-key, and double-key read paths simultaneously, all
     * loaded from one shared source list.
     */
    @Test
    @DisplayName("One object exposes all three cache shapes (unique/group/double-key) simultaneously")
    void oneObjectExposesAllThreeCacheShapesSimultaneously() {
        ProviderCatalog catalog = new ProviderCatalog();
        catalog.refresh(sourceData());

        // UniqueCache.get(k)
        Item byId1 = catalog.byId("1");
        assertNotNull(byId1, "unique-key read should find item id=1");
        assertEquals("fruit", byId1.category());

        // GroupedCache.get(k) -> list
        List<Item> fruits = catalog.byCategory("fruit");
        assertEquals(2, fruits.size(), "group-key read should return all fruit items");
        assertTrue(fruits.stream().anyMatch(i -> i.id().equals("1")));
        assertTrue(fruits.stream().anyMatch(i -> i.id().equals("2")));

        // DoubleKeyCache.get(k1, k2)
        Item byBucket = catalog.byCategoryAndBucket("veggie", "leafy");
        assertNotNull(byBucket, "double-key read should find item at (veggie, leafy)");
        assertEquals("3", byBucket.id());

        // Sanity: an object of this kind was previously impossible under
        // single inheritance -- proven here simply by these three typed
        // reads succeeding together on one instance.
        assertEquals(Map.of("byId", 4, "byCategory", 2, "byCategoryAndBucket", 2), catalog.sizes());
    }

    /**
     * Failure atomicity, positive direction: a successful refresh (stage all
     * three views, then publish all three) advances all three views' content
     * together.
     */
    @Test
    @DisplayName("Successful refresh advances all three views together")
    void successfulRefreshAdvancesAllViewsTogether() {
        ProviderCatalog catalog = new ProviderCatalog();

        // seed with an initial, smaller dataset
        List<Item> initial = List.of(new Item("1", "fruit", "sweet"));
        catalog.refresh(initial);
        assertEquals(Map.of("byId", 1, "byCategory", 1, "byCategoryAndBucket", 1), catalog.sizes());

        // now refresh with the full dataset
        catalog.refresh(sourceData());

        assertEquals(Map.of("byId", 4, "byCategory", 2, "byCategoryAndBucket", 2), catalog.sizes());
        assertNotNull(catalog.byId("2"));
        assertEquals(2, catalog.byCategory("fruit").size());
        assertNotNull(catalog.byCategoryAndBucket("veggie", "root"));
    }

    /**
     * Failure atomicity, negative direction (design §4.1): {@code stage()}
     * is pure/build-only and can throw; {@code publish()} is a volatile swap
     * that cannot throw. If the second view's {@code stage()} throws during a
     * refresh, no view should have been published -- every view must still
     * hold its previous snapshot, not a partial mix of old and new data.
     */
    @Test
    @DisplayName("A stage() failure on one view leaves every view's previous snapshot untouched")
    void stageFailureOnOneViewLeavesAllViewsAtPreviousSnapshot() {
        ProviderCatalog catalog = new ProviderCatalog();

        // seed all three views with known initial data
        List<Item> initial = List.of(
                new Item("1", "fruit", "sweet"),
                new Item("2", "veggie", "leafy")
        );
        catalog.refresh(initial);
        Map<String, Integer> sizesBefore = catalog.sizes();
        assertEquals(Map.of("byId", 2, "byCategory", 2, "byCategoryAndBucket", 2), sizesBefore);

        // Drive a stage() failure via a duplicate id: refresh()'s ordering
        // stages byId first, so this fails at the FIRST stage call, before
        // byCategory or byCategoryAndBucket are even staged. Note that
        // GroupedCache's stage() never throws on duplicate keys (duplicates
        // are its normal, expected case), so a genuine "stage() throws"
        // failure can only originate from a UniqueCache or DoubleKeyCache
        // view; the companion test below drives the failure from the
        // DoubleKeyCache view instead, to prove the guarantee holds
        // regardless of which view's stage() throws.
        List<Item> badSource = List.of(
                new Item("dup", "fruit", "sweet"),
                new Item("dup", "veggie", "leafy") // duplicate id -> UniqueCache.stage() throws
        );

        assertThrows(IllegalStateException.class, () -> catalog.refresh(badSource),
                "refresh() must propagate stage() failure without publishing anything");

        // Every view must still reflect the PREVIOUS snapshot, not empty and
        // not the new (bad) data, and not a partial mix.
        Map<String, Integer> sizesAfter = catalog.sizes();
        assertEquals(sizesBefore, sizesAfter,
                "sizes after a failed refresh must equal sizes before it (no view mutated)");

        Item byId1 = catalog.byId("1");
        assertNotNull(byId1, "byId view must still hold its previous snapshot");
        assertEquals("fruit", byId1.category());

        List<Item> fruitsAfter = catalog.byCategory("fruit");
        assertEquals(1, fruitsAfter.size(), "byCategory view must still hold its previous snapshot");
        assertEquals("1", fruitsAfter.get(0).id());

        Item byBucketAfter = catalog.byCategoryAndBucket("veggie", "leafy");
        assertNotNull(byBucketAfter, "byCategoryAndBucket view must still hold its previous snapshot");
        assertEquals("2", byBucketAfter.id());

        // The "dup" id from the failed refresh must not have leaked into any view.
        assertEquals(null, catalog.byId("dup"), "failed refresh must not partially publish new data");
    }

    /**
     * Companion negative case: drive the failure at the second staged view
     * within refresh() (byCategoryAndBucket, a DoubleKeyCache, staged after
     * byId and byCategory) to prove the guarantee holds regardless of which
     * view's stage() throws, not only the first one attempted.
     */
    @Test
    @DisplayName("A stage() failure on the double-key view also leaves every view untouched")
    void stageFailureOnDoubleKeyViewLeavesAllViewsAtPreviousSnapshot() {
        ProviderCatalog catalog = new ProviderCatalog();

        List<Item> initial = List.of(
                new Item("1", "fruit", "sweet"),
                new Item("2", "veggie", "leafy")
        );
        catalog.refresh(initial);
        Map<String, Integer> sizesBefore = catalog.sizes();

        // byId keys are unique (ok), byCategory duplicates are fine (ok),
        // but (category, bucket) pair repeats -> DoubleKeyCache.stage() throws.
        List<Item> badSource = List.of(
                new Item("10", "fruit", "sweet"),
                new Item("11", "fruit", "sweet") // duplicate (category, bucket) pair
        );

        assertThrows(IllegalStateException.class, () -> catalog.refresh(badSource));

        assertEquals(sizesBefore, catalog.sizes(),
                "no view should be mutated when a later-staged view fails");
        assertNotNull(catalog.byId("1"));
        assertEquals(1, catalog.byCategory("fruit").size());
        assertNotNull(catalog.byCategoryAndBucket("veggie", "leafy"));
        assertEquals(null, catalog.byId("10"), "new data from the failed refresh must not leak in");
    }
}
