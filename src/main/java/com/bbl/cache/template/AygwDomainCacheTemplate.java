//package com.bbl.cache.template;
//
//import com.bbl.common.application.port.out.persistence.*;
//import com.bbl.common.cache.*;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.*;
//import jakarta.inject.Inject;
//
//import java.util.List;
//
//
//public abstract class AygwDomainCacheTemplate {
//
//    @Inject
//    private AnygwCompProfileRepositoryPort compProfileRepo;
//
//    @Inject
//    private AnygwPropertiesRepositoryPort propertyRepo;
//
//    @Inject
//    private AnygwResponseMappingRepositoryPort responseMappingRepo;
//
//    @Inject
//    private ServiceProviderProfileRepositoryPort serviceProviderRepo;
//
//    @Inject
//    private AnygwServiceProfileRepositoryPort serviceProfileRepo;
//
//    @Inject
//    private ServiceClientProfileRepositoryPort clientProfileRepo;
//
//    private volatile AnygwCompProfileCache compProfileCache;
//    private volatile AnygwPropertiesCache propertiesCache;
//    private volatile AnygwResponseMappingCache responseMappingCache;
//    private volatile ServiceProviderProfileCache serviceProviderProfileCache;
//    private volatile AnygwServiceProfileCache anygwServiceProfileCache;
//    private volatile ServiceClientProfileCache serviceClientProfileCache;
//
//    protected abstract List<String> serviceNameList();
//
//    protected abstract List<String> categoryList();
//
//    protected abstract String productCode();
//
//    protected AnygwCompProfileCache createCompProfileCache() {
//        if (compProfileCache == null) {
//            compProfileCache =
//                    new AnygwCompProfileCache(
//                            compProfileRepo,
//                            serviceNameList());
//        }
//        return compProfileCache;
//    }
//
//    protected AnygwPropertiesCache cretePropertyCache() {
//        if (propertiesCache == null) {
//            propertiesCache = new AnygwPropertiesCache(propertyRepo, productCode(), categoryList());
//        }
//        return propertiesCache;
//    }
//
//    protected AnygwResponseMappingCache createResponseMappingCache() {
//        if (responseMappingCache == null) {
//            responseMappingCache = new AnygwResponseMappingCache(responseMappingRepo, serviceNameList());
//        }
//        return responseMappingCache;
//    }
//
//    protected ServiceProviderProfileCache createServiceProviderProfileCache() {
//        if (serviceProviderProfileCache == null) {
//            serviceProviderProfileCache = new ServiceProviderProfileCache(serviceProviderRepo, serviceNameList());
//        }
//        return serviceProviderProfileCache;
//    }
//
//    protected AnygwServiceProfileCache createAnygwServiceProfile() {
//        if (anygwServiceProfileCache == null) {
//            anygwServiceProfileCache = new AnygwServiceProfileCache(serviceProfileRepo, serviceNameList());
//        }
//        return anygwServiceProfileCache;
//    }
//
//    protected ServiceClientProfileCache createServiceClientProfileCache() {
//        if (serviceClientProfileCache == null) {
//            serviceClientProfileCache = new ServiceClientProfileCache(clientProfileRepo, serviceNameList());
//        }
//        return serviceClientProfileCache;
//    }
//
//    public AnygwCompProfileCache getCompProfileCache() {
//        return createCompProfileCache();
//    }
//
//    public AnygwPropertiesCache getPropertiesCache() {
//        return cretePropertyCache();
//    }
//
//    public AnygwResponseMappingCache getResponseMappingCache() {
//        return createResponseMappingCache();
//    }
//
//    public ServiceProviderProfileCache getServiceProviderProfileCache() {
//        return createServiceProviderProfileCache();
//    }
//
//    public AnygwServiceProfileCache getAnygwServiceProfileCache() {
//        return createAnygwServiceProfile();
//    }
//
//    public ServiceClientProfileCache getServiceClientProfileCache() {
//        return createServiceClientProfileCache();
//    }
//
//}
