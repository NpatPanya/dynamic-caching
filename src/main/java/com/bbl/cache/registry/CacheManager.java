package com.bbl.cache.registry;


import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class CacheManager {

    @Inject
    private CacheRegistry cacheRegistry;

//    @Inject
//    private Instance<CacheLoader<?>> cacheLoaderInstance;


    @Inject
    AnygwCompProfileCache compProfileCache;


//    @PostConstruct
//    void init() {
//
//        for (CacheLoader<?> cacheLoader : cacheLoaderInstance) {
//            cacheRegistry.register(cacheLoader);
//        }
//    }

    @PostConstruct
    void init(){
        cacheRegistry.register(compProfileCache);
    }
}