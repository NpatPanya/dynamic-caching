package com.bbl.cache.example.entity;

public class ResponseEntityId {

    private String serviceName;
    private String clientId;
    private String providerId;
    private String providerRspCode;


    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderRspCode() {
        return providerRspCode;
    }

    public void setProviderRspCode(String providerRspCode) {
        this.providerRspCode = providerRspCode;
    }
}
