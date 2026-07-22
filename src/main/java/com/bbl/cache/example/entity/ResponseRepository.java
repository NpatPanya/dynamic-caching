package com.bbl.cache.example.entity;

import java.util.List;

public interface ResponseRepository {

    List<ResponseEntity> findByServiceNameList(List<String> serviceNameList);
}
