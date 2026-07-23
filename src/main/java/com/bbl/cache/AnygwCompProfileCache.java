//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.AnygwCompProfileRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.DoubleKeyCache;
//import com.bbl.common.cache.temp.registry.GroupedCache;
//import com.bbl.common.cache.temp.registry.UniqueCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwcompprofile;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//
//
//public class AnygwCompProfileCache {
//
//
//    private static final Logger log = LogManager.getLogger();
//
//
//    private final CacheFacade cacheFacade = new CacheFacade(this.getClass().getName(), log);
//    private final DoubleKeyCache<String, String, Anygwcompprofile> doubleKeyCache = cacheFacade.doubleKeyCache("doubleKeyCompProfileCache");
//    private final UniqueCache<String, Anygwcompprofile> uniqueCache = cacheFacade.uniqueCache("uniqueCompProfileCache");
//    private final GroupedCache<String, Anygwcompprofile> groupCache = cacheFacade.groupedCache("compProfieGroupCache");
//
//
//    private final AnygwCompProfileRepositoryPort port;
//    private final List<String> serviceNameList;
//
//
//    public AnygwCompProfileCache(AnygwCompProfileRepositoryPort port, List<String> serviceNameList) {
//        this.port = port;
//        this.serviceNameList = serviceNameList;
//
//    }
//
//
//
//
//    public List<Anygwcompprofile> getQueryResult() {
//        return port.findByServiceNameList(serviceNameList);
//    }
//
//
//    //load double key cache (k1 = serviceName,k2 = compId, value = anygwcompprofile)
//    //use case : multiple serviceName, filtered with compId, expected return anygwcompprofile of each servicename with exact compId
//    public void initDoubleKeyCache() {
//        doubleKeyCache.load(getQueryResult(),
//                f -> f.getId().getServiceName(),
//                s -> s.getId().getCompId());
//    }
//
//
//    //load group cache (k1 = serviceName, value = List.of(anygwcompprofile))
//    //use case : multiple serviceName , want value to return as List<comprofile> categorize by serviceName
//    public void initGroupCache() {
//        groupCache.load(getQueryResult(), f -> f.getId().getServiceName());
//    }
//
//    //load uniqueCache(k1 = compId, value = anygwcompprofile)
//    //use case : single service name
//    public void initUniqueCache() {
//        uniqueCache.load(getQueryResult(), f -> f.getId().getCompId());
//    }
//
//    public DoubleKeyCache<String, String, Anygwcompprofile> getDoubleKeyCache() {
//        return doubleKeyCache;
//    }
//
//    public UniqueCache<String, Anygwcompprofile> getUniqueCache() {
//        return uniqueCache;
//    }
//
//    public GroupedCache<String, Anygwcompprofile> getGroupCache() {
//        return groupCache;
//    }
//}
