package com.bbl.cache.example.entity;

import java.util.List;

public interface ProviderRepository {

    List<Provider> findByServiceNames(List<String> serviceNameList);
}
