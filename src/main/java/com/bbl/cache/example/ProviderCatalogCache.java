package com.bbl.cache.example;

// NOTE: non-compiling stub — references illustrative Provider/ProviderRepositoryPort types not present in this module (see design doc §6.4, §9.2)

import com.bbl.cache.example.entity.Provider;
import com.bbl.cache.example.entity.ProviderRepository;
import com.bbl.cache.registry.deprecated.CacheFacade;
import com.bbl.cache.registry.deprecated.DoubleKeyCache;
import com.bbl.cache.registry.deprecated.GroupedCache;
import com.bbl.cache.registry.deprecated.UniqueCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Multi-shape cache demonstrating one bean holding three distinct cache views
 * ({@link UniqueCache}, {@link GroupedCache}, {@link DoubleKeyCache}) built from
 * a single materialized source, with failure-atomic refresh semantics.
 *
 * <p>This bean proves the core requirement of the cache redesign: a single
 * application bean can now expose multiple cache shapes simultaneously without
 * forcing inheritance conflicts.
 *
 * <p>The {@link #refresh(List)} method materializes the source once, stages
 * all views (aborting atomically on duplicate-key failure), then publishes all
 * views together. This ensures no view is left partially updated if another fails.
 */
@ApplicationScoped
public class ProviderCatalogCache {

    private static final Logger log = LogManager.getLogger();

    // FIRST: CacheGroup must be initialized before any shape field (see design spec §5)
    private final CacheFacade caches = new CacheFacade("ProviderCatalogCache", log);

    // Three shapes, side by side — impossible under the old inheritance model
    private final UniqueCache<String, Provider> byId =
            caches.uniqueCache("byId");

    private final UniqueCache<String, String> byIdExtractedValue =
            caches.uniqueCache("byIdExtractedValue");

    private final GroupedCache<String, Provider> byCountry =
            caches.groupedCache("byCountry");

    private final GroupedCache<String, String> byCountryExtractedValue =
            caches.groupedCache("byCountryExtractedValue");

    private final DoubleKeyCache<String, String, Provider> byServiceProvider =
            caches.doubleKeyCache("byServiceProvider");

    private final DoubleKeyCache<String, String, String> byServiceProviderExtractedValue =
            caches.doubleKeyCache("byServiceProviderExtractedValue");


    private ProviderRepository repo;

    public ProviderCatalogCache() {
    }

    @Inject
    public ProviderCatalogCache(ProviderRepository repo) {
        this.repo = repo;
    }

    /**
     * Refreshes all three cache views from a single fetch, with failure-atomic semantics.
     *
     * <p>Process:
     * <ol>
     *   <li>Materialize the source once into a stable {@link List} (required because
     *       {@link #stage} streams its input once per call; a lazy/single-pass source
     *       would be exhausted after the first view if not copied).</li>
     *   <li>Stage (build) all three snapshots. If any duplicate-key error occurs here,
     *       no publish has run yet, so all views retain their previous snapshots.</li>
     *   <li>Publish (swap) all three snapshots. Each publish is a single volatile write
     *       that cannot throw; once staging succeeds, all publishes succeed together.</li>
     * </ol>
     *
     * @param serviceNames list of service names to fetch providers for
     * @throws IllegalStateException if any view has a duplicate key
     */
    public void refresh(List<String> serviceNames) {
        // 1. Materialize ONCE (re-iterable) — see design doc §4.3
        List<Provider> src = List.copyOf(repo.findByServiceNames(serviceNames));

        // 2. Build all snapshots (any dup-key throws here, before any publish)
        var idMap = byId.stage(src, Provider::getId);
        var idExtractedValueMap = byIdExtractedValue.stage(src, Provider::getId, Provider::getServiceName);
        var countryMap = byCountry.stage(src, Provider::getCountry);
        var countryExtractedValue = byCountryExtractedValue.stage(src, Provider::getCountry, Provider::getServiceName);
        var svcProvMap = byServiceProvider.stage(src, Provider::getServiceName, Provider::getId);
        var svcProvMapExtractedValue = byServiceProviderExtractedValue.stage(src, Provider::getServiceName, Provider::getCountry, Provider::getId);

        // 3. Publish — cannot throw; all views advance together
        byId.publish(idMap);
        byCountry.publish(countryMap);
        byServiceProvider.publish(svcProvMap);
    }

    // ---- Typed reads through the fields ----

    /**
     * Retrieves a provider by unique ID.
     *
     * @param id provider ID
     * @return matching provider, or null if not found
     */
    public Provider byId(String id) {
        return byId.get(id);
    }

    /**
     * Retrieves all providers for a given country.
     *
     * @param country country code or name
     * @return immutable list of matching providers (empty if none found)
     */
    public List<Provider> byCountry(String country) {
        return byCountry.get(country);
    }

    /**
     * Retrieves a provider by service name and provider ID.
     *
     * @param svc service name
     * @param id  provider ID within that service
     * @return matching provider, or null if not found
     */
    public Provider byServiceProvider(String svc, String id) {
        return byServiceProvider.get(svc, id);
    }

    // ---- Cross-cutting through the group ----

    /**
     * Clears all three cache views.
     */
    public void clearAll() {
        caches.clearAll();
    }

    /**
     * Returns the current size (entry count) of each cache view.
     *
     * @return immutable map of view name to size, preserving registration order
     */
    public Map<String, Integer> sizes() {
        return caches.sizes();
    }
}
