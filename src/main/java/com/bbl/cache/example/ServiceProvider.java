package com.bbl.cache.example;



import java.util.Objects;
@Deprecated
public class ServiceProvider {
    private ServiceProviderId id;
    private String providerName;
    private String serviceApp;

    public ServiceProvider(ServiceProviderId id, String providerName, String serviceApp) {
        this.id = id;
        this.providerName = providerName;
        this.serviceApp = serviceApp;
    }

    public ServiceProviderId getId() {
        return id;
    }

    public void setId(ServiceProviderId id) {
        this.id = id;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getServiceApp() {
        return serviceApp;
    }

    public void setServiceApp(String serviceApp) {
        this.serviceApp = serviceApp;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServiceProvider that = (ServiceProvider) o;
        return Objects.equals(id, that.id) && Objects.equals(providerName, that.providerName) && Objects.equals(serviceApp, that.serviceApp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, providerName, serviceApp);
    }
}
