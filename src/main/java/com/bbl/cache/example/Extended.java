package com.bbl.cache.example;

import com.bbl.cache.example.entity.Provider;
import com.bbl.cache.example.entity.ProviderRepository;
import com.bbl.cache.support.ViewFactory;

import java.util.List;
import java.util.Map;

public class Extended extends ProviderCatalogCache {


    public Extended() {
        super();
    }

    public Extended(ProviderRepository repo) {
        super(repo);
    }

    @Override
    public void refresh(List<String> serviceNames) {
        super.refresh(serviceNames);
    }

    @Override
    public Provider byId(String id) {
        return super.byId(id);
    }

    @Override
    public List<Provider> byCountry(String country) {
        return super.byCountry(country);
    }

    @Override
    public Provider byServiceProvider(String svc, String id) {
        return super.byServiceProvider(svc, id);
    }

    @Override
    public void clearAll() {
        super.clearAll();
    }

    @Override
    public Map<String, Integer> sizes() {
        return super.sizes();
    }

    public void test() {
        List<Provider> list = List.of();
        ViewFactory.filteredView(list,Provider::getCountry);

    }
}
