package com.bbl.cache.template;



import java.util.List;


public interface AnygwCompProfileRepositoryPort {

    List<Anygwcompprofile> findByServiceNameList(List<String> serviceNameLis);

    Anygwcompprofile getById(String compId, String serviceName, String bpmBillerId);

    Anygwcompprofile getByCompIdAndServiceNameAndClientId(String compId, String serviceName, String clientId);

    List<Anygwcompprofile> findByServiceNameAndClientId(String serviceName, String clientId);
}
