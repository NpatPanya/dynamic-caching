//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.ServiceClientProfileRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.DoubleKeyCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Serviceclientprofile;
//import jakarta.enterprise.context.ApplicationScoped;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//
//@ApplicationScoped
//public class ServiceClientProfileCache {
//
//    private static final Logger log = LogManager.getLogger();
//
//
//    private final CacheFacade serviceClientCache = new CacheFacade(this.getClass().getName(), log);
//    private final DoubleKeyCache<String, String, Serviceclientprofile> doubleKeyCache = serviceClientCache.doubleKeyCache("doubleKeyCache");
//
//    private final ServiceClientProfileRepositoryPort port;
//    private final List<String> serviceNameList;
//
//
//    public ServiceClientProfileCache(ServiceClientProfileRepositoryPort port, List<String> serviceNameList) {
//        this.port = port;
//        this.serviceNameList = serviceNameList;
//    }
//
//
//    public List<Serviceclientprofile> getQuery() {
//        return port.findByServiceNameList(serviceNameList);
//    }
//
//    /**
//     * Loads client profiles for the specified services.
//     *
//     * <p>Primary key = serviceName
//     *
//     * <p>Secondary key = clientId
//     *
//     * <p>Value = Serviceclientprofile
//     */
//    public void initDoubleKeyCache() {
//        doubleKeyCache.load(getQuery(), f -> f.getId().getServiceName(), s -> s.getId().getClientId());
//    }
//
//    public DoubleKeyCache<String, String, Serviceclientprofile> getDoubleKeyCache() {
//        return doubleKeyCache;
//    }
//}
