package com.bbl.cache.example;

// NOTE: excluded from compilation like the other example classes (see pom.xml
// com/bbl/cache/example/** exclude) — references illustrative Provider/ProviderRepository
// types the way ProviderCatalogCache.java does. Written correct by hand; not
// compile-checked by Maven.

import com.bbl.cache.example.entity.Provider;
import com.bbl.cache.example.entity.ProviderRepository;
import com.bbl.cache.registry.Cache;
import com.bbl.cache.registry.CacheDescriptor;
import com.bbl.cache.registry.CacheRegistry;
import com.bbl.cache.registry.Caches;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;

/**
 * Construct-and-register pattern for the v3 composition-based redesign,
 * shown alongside (not replacing) the existing inheritance-based
 * {@link ProviderCatalogCache} example.
 *
 * <p>No subclass, no shared facade, no CDI-bean-managed cache lifecycle is
 * required to build and use a cache: a caller builds one directly via
 * {@link Caches} and registers it under a {@link CacheDescriptor} with
 * {@link CacheRegistry}. A CDI bean here is only a thin trigger that
 * refreshes the registry entry on a schedule; it never becomes a
 * {@code Cache} implementation itself.
 */
@ApplicationScoped
public class ProviderCatalogRegistryUsage {

    private static final CacheDescriptor<String, Provider> BY_ID =
            CacheDescriptor.of("provider-by-id", String.class, Provider.class);

    private final ProviderRepository repo;

    @Inject
    public ProviderCatalogRegistryUsage(ProviderRepository repo) {
        this.repo = repo;
    }

    /**
     * Builds a fresh snapshot from the repository and registers/reloads it
     * atomically under {@link #BY_ID}, with a 10-minute TTL.
     *
     * <p>First call creates the entry (upsert); subsequent calls (e.g. on a
     * schedule) atomically replace the previous snapshot -- no {@code Cache}
     * instance is ever mutated in place.
     */
    public void refresh(List<String> serviceNames) {
        List<Provider> providers = List.copyOf(repo.findByServiceNames(serviceNames));
        Cache<String, Provider> fresh = Caches.fromList(providers, Provider::getId);

        CacheRegistry.getInstance().reregister(BY_ID, fresh, Duration.ofMinutes(10));
    }

    public Provider byId(String id) {
        return CacheRegistry.getInstance().get(BY_ID)
                .map(cache -> cache.get(id))
                .orElse(null);
    }
}
