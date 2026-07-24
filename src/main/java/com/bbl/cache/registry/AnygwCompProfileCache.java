package com.bbl.cache.registry;


import com.bbl.cache.support.DataFilter;
import com.bbl.cache.template.AnygwCompProfileRepositoryPort;
import com.bbl.cache.template.Anygwcompprofile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AnygwCompProfileCache
        implements CacheLoader<Map<String, Map<String, Anygwcompprofile>>> {

    @Inject
    AnygwCompProfileRepositoryPort port;

    private final List<String> serviceNameList =
            List.of("test", "test");

    @Override
    public String getName() {
        return "ComProfileCache";
    }

    private Map<String, Map<String, Anygwcompprofile>> compProfileCache = new ConcurrentHashMap<>();

    @Override
    public  Map<String, Map<String, Anygwcompprofile>> load() {

        var result = port.findByServiceNameList(serviceNameList);

        compProfileCache = DataFilter.filterToNestedMap(
                result,
                ignore -> true,
                f -> f.getId().getServiceName(),
                s -> s.getId().getCompId()
        );
        return compProfileCache;
    }

    @Override
    public Map<String, Map<String, Anygwcompprofile>> get() {
        return compProfileCache;
    }
}
