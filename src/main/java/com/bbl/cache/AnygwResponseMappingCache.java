//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.AnygwResponseMappingRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.DoubleKeyCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwresponsemapping;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//
///**
// * Cache for provider response-code mappings.
// *
// * <p>Cache structure:
// *
// * <pre>
// * (serviceName, clientId)
// *     -> (providerId, providerRspCode)
// *         -> Anygwresponsemapping
// * </pre>
// *
// * <p>This cache is used to translate provider response codes into
// * client-facing response codes and messages.
// */
//
//public class AnygwResponseMappingCache {
//
//    private static final Logger log = LogManager.getLogger();
//
//    private final CacheFacade cacheFacade = new CacheFacade(this.getClass().getName(), log);
//
//    private final DoubleKeyCache<ServiceClientKey, ProviderResponseKey, Anygwresponsemapping> doubleKeyCache = cacheFacade.doubleKeyCache("responseMappingDoubleKeyCache");
//
//
//    private final AnygwResponseMappingRepositoryPort port;
//    private final List<String> serviceNameList;
//
//    public AnygwResponseMappingCache(AnygwResponseMappingRepositoryPort port, List<String> serviceNameList) {
//        this.port = port;
//        this.serviceNameList = serviceNameList;
//    }
//
//
//    public List<Anygwresponsemapping> getQuery() {
//        return port.findByServiceNameList(serviceNameList);
//    }
//
//    public void initDoubleKeyCache() {
//        doubleKeyCache.load(getQuery(),
//                f -> new ServiceClientKey(
//                        f.getId().getServiceName(),
//                        f.getId().getClientId()),
//                s -> new ProviderResponseKey(
//                        s.getId().getProviderId(),
//                        s.getId().getProviderRspcode()));
//
//    }
//
//    public DoubleKeyCache<ServiceClientKey, ProviderResponseKey, Anygwresponsemapping> getDoubleKeyCache() {
//        return doubleKeyCache;
//    }
//
//    public record ServiceClientKey(String serviceName, String clientId) {
//    }
//
//    public record ProviderResponseKey(String providerId, String providerRspCode) {
//
//    }
//
//}
