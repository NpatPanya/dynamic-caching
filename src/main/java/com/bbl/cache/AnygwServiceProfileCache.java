//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.AnygwServiceProfileRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.UniqueCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwserviceprofile;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//
//
//public class AnygwServiceProfileCache {
//
//    private static final Logger log = LogManager.getLogger();
//
//    private final CacheFacade serviceProfileCache = new CacheFacade(this.getClass().getName(), log);
//    private final UniqueCache<String, Anygwserviceprofile> uniqueCache = serviceProfileCache.uniqueCache("uniqueCache");
//    private final AnygwServiceProfileRepositoryPort repo;
//    private final List<String> serviceNameList;
//
//
//    public AnygwServiceProfileCache(AnygwServiceProfileRepositoryPort repo, List<String> serviceNameList) {
//        this.repo = repo;
//        this.serviceNameList = serviceNameList;
//    }
//
//
//    public List<Anygwserviceprofile> getQuery() {
//        return repo.findByServiceNameList(serviceNameList);
//    }
//
//    /**
//     * Cache for service profile configuration.
//     *
//     * <p>Key:
//     * <pre>
//     * serviceName
//     * </pre>
//     *
//     * <p>Value:
//     * <pre>
//     * Anygwserviceprofile
//     * </pre>
//     */
//    public void initUniqueCache() {
//
//        uniqueCache.load(getQuery(), s -> s.getId().getServiceName());
//    }
//
//    public UniqueCache<String, Anygwserviceprofile> getUniqueCache() {
//        return uniqueCache;
//    }
//
//
//}
