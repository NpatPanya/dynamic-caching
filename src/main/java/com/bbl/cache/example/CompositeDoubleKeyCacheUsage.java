package com.bbl.cache.example;

import com.bbl.common.application.port.out.persistence.AnygwResponseMappingRepositoryPort;
import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwresponsemapping;
import com.bbl.gw.config.cache.registry.DoubleKeyCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Cache for provider response-code mappings.
 *
 * <p>Cache structure:
 *
 * <pre>
 * (serviceName, clientId)
 *     -> (providerId, providerRspCode)
 *         -> Anygwresponsemapping
 * </pre>
 *
 * <p>This cache is used to translate provider response codes into
 * client-facing response codes and messages.
 */
@ApplicationScoped
public class CompositeDoubleKeyCacheUsage extends DoubleKeyCache<
        CompositeDoubleKeyCacheUsage.ServiceClientKey,
        CompositeDoubleKeyCacheUsage.ProviderResponseKey,
        Entitiy3> {

    private static final Logger log = LogManager.getLogger();
    private Entitiy3RepositoryPort port;

    public CompositeDoubleKeyCacheUsage() {
    }

    @Inject
    public CompositeDoubleKeyCacheUsage(Entitiy3RepositoryPort port) {
        this.port = port;
    }


    /**
     * Loads response mappings for the specified services.
     *
     * <p>Primary key:
     * <pre>
     * (serviceName, clientId)
     * </pre>
     *
     * <p>Secondary key:
     * <pre>
     * (providerId, providerRspCode)
     * </pre>
     *
     * <p>Value:
     * <pre>
     * Entitiy3
     * </pre>
     */
    public void init(List<String> serviceNameList) {
        var result = port.findByServiceNameList(serviceNameList);
        load(result,
                f -> new ServiceClientKey(
                        f.getId().getServiceName(),
                        f.getId().getClientId()),
                s -> new ProviderResponseKey(
                        s.getId().getProviderId(),
                        s.getId().getProviderRspcode()));

    }

    @Override
    protected Logger logger() {
        return log;
    }


    public record ServiceClientKey(String serviceName, String clientId) {
    }

    public record ProviderResponseKey(String providerId, String providerRspCode) {

    }

}
