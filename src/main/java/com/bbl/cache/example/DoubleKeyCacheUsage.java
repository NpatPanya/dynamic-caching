package com.bbl.cache.example;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@ApplicationScoped
public class DoubleKeyCacheUsage extends DoubleKeyCache<String, String, Entitiy1> {

    private static final Logger log = LogManager.getLogger();

    private ExampleRepo port;

    public DoubleKeyCacheUsage() {
    }

    @Inject
    public DoubleKeyCacheUsage(ExampleRepo port) {
        this.port = port;
    }

    @Override
    protected Logger logger() {
        return log;
    }

    /**
     * Loads provider profiles for the specified services.
     *
     * <p>Primary key = serviceName
     *
     * <p>Secondary key = providerId
     *
     * <p>Value = Entitiy1
     */
    public void init(List<String> serviceNameList) {
        var result = port.findByServiceNameList(serviceNameList);

        load(result,
                p -> p.getId().getServiceName(),
                p -> p.getId().getProviderId());
    }

}
