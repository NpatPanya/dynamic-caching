package com.bbl.cache.example;

import java.util.Objects;
@Deprecated
public class ServiceProviderId {


    private  String providerId;
    private  String ServiceName;

    public ServiceProviderId(String providerId, String serviceName) {
        this.providerId = providerId;
        ServiceName = serviceName;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getServiceName() {
        return ServiceName;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public void setServiceName(String serviceName) {
        ServiceName = serviceName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServiceProviderId that = (ServiceProviderId) o;
        return Objects.equals(providerId, that.providerId) && Objects.equals(ServiceName, that.ServiceName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, ServiceName);
    }
}
