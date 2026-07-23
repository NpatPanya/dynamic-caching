package com.bbl.cache.template;//package com.bbl.common.cache.template;
//
//
//import com.bbl.common.cache.AnygwPropertiesCache;
//import com.bbl.common.cache.AnygwResponseMappingCache;
//import com.bbl.common.cache.ServiceProviderProfileCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwresponsemapping;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Serviceproviderprofile;
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.inject.Inject;
//
//import java.util.List;
//import java.util.Map;
//
//@ApplicationScoped
//public abstract class AnygwDomainCommonCache {
//
//    @Inject
//    private AnygwPropertiesCache anygwPropertiesCache;
//    @Inject
//    private AnygwResponseMappingCache anygwResponseMappingCache;
//    @Inject
//    private ServiceProviderProfileCache serviceProviderProfileCache;
//
//    protected AnygwDomainCommonCache() {
//    }
//
//    protected abstract List<String> serviceNameList();
//
//    protected abstract List<String> categoryList();
//
//    protected abstract String productCode();
//
//
//    public void initAnygwPropertyCache() {
//        anygwPropertiesCache.initDoubleKeyCache(productCode(), categoryList());
//    }
//
//    public Map<String, Map<String, String>> getPropertyCache() {
//        return anygwPropertiesCache.asMap();
//    }
//
//    public void initResponseMappingCache() {
//        anygwResponseMappingCache.initDoubleKeyCache(serviceNameList());
//    }
//
//    public Map<AnygwResponseMappingCache.ServiceClientKey, Map<AnygwResponseMappingCache.ProviderResponseKey, Anygwresponsemapping>> getResponseMappingCache() {
//        return anygwResponseMappingCache.asMap();
//    }
//
//    public void initServiceProviderProfileCache() {
//        serviceProviderProfileCache.initServiceProviderProfileDoubleKeyCache(serviceNameList());
//    }
//
//    public Map<String, Map<String, Serviceproviderprofile>> getServiceProviderProfileCache() {
//        return serviceProviderProfileCache.asMap();
//    }
//
//
//}
