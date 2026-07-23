//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.ServiceProviderProfileRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.DoubleKeyCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Serviceproviderprofile;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//import java.util.Objects;
//
//
//public class ServiceProviderProfileCache {
//
//    private static final Logger log = LogManager.getLogger();
//
//    private final CacheFacade cacheFacade = new CacheFacade(this.getClass().getName(), log);
//    private final DoubleKeyCache<String, String, Serviceproviderprofile> serviceProviderDoubleKeyCache = cacheFacade.doubleKeyCache("serviceProviderDoubleKeyCache");
//
//    private final ServiceProviderProfileRepositoryPort port;
//    private final List<String> serviceNameList;
//
//
//    public ServiceProviderProfileCache(ServiceProviderProfileRepositoryPort port, List<String> serviceNameList) {
//        this.port = Objects.requireNonNull(port, "ServiceProviderProfileRepository is null");
//        this.serviceNameList = Objects.requireNonNull(serviceNameList, "ServiceNameList is null");
//    }
//
//
//    public List<Serviceproviderprofile> getQuery() {
//        return port.findByServiceNameList(serviceNameList);
//    }
//
//
//    public void initDoubleKeyCache() {
//
//        serviceProviderDoubleKeyCache.load(getQuery(),
//                p -> p.getId().getServiceName(),
//                p -> p.getId().getProviderId());
//    }
//
//    public DoubleKeyCache<String, String, Serviceproviderprofile> getDoubleKeyCache() {
//        return serviceProviderDoubleKeyCache;
//    }
//
//}
