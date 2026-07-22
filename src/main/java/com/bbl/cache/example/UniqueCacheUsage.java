package com.bbl.cache.example;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;


@ApplicationScoped
public class UniqueCacheUsage extends UniqueCache<String, Entitiy2> {

    private static final Logger log = LogManager.getLogger();

    private Entitiy2RepositoryPort repo;


    public UniqueCacheUsage() {
    }

    @Inject
    public UniqueCacheUsage(Entitiy2RepositoryPort repo) {
        this.repo = repo;
    }


    @Override
    protected Logger logger() {
        return log;
    }

    /**
     * Cache for service profile configuration.
     *
     * <p>Key:
     * <pre>
     * serviceName
     * </pre>
     *
     * <p>Value:
     * <pre>
     * Entitiy2
     * </pre>
     */
    public void init(List<String> serviceNameList) {
        var result = repo.findByServiceNameList(serviceNameList);
        load(result, s -> s.getId().getServiceName());
    }



}
