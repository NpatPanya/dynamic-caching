package com.bbl.cache.example;

// NOTE: non-compiling stub — references example entity/repo types not present in this module (see design doc §9.2)

import com.bbl.cache.example.entity.ResponseEntity;
import com.bbl.cache.example.entity.ResponseRepository;
import com.bbl.cache.registry.CacheFacade;
import com.bbl.cache.registry.DoubleKeyCache;
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
public class CompositeDoubleKeyCacheUsage {

    private static final Logger log = LogManager.getLogger();

    private final CacheFacade caches = new CacheFacade("CompositeDoubleKeyCacheUsage", log);
    private final DoubleKeyCache<ServiceClientKey, ProviderResponseKey, ResponseEntity> byKeys =
            caches.doubleKeyCache("byKeys");

    private ResponseRepository port;

    public CompositeDoubleKeyCacheUsage() {
    }

    @Inject
    public CompositeDoubleKeyCacheUsage(ResponseRepository port) {
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
        byKeys.load(result,
                f -> new ServiceClientKey(
                        f.getId().getServiceName(),
                        f.getId().getClientId()),
                s -> new ProviderResponseKey(
                        s.getId().getProviderId(),
                        s.getId().getProviderRspCode()));

    }

    public ResponseEntity get(ServiceClientKey k1, ProviderResponseKey k2) {
        return byKeys.get(k1, k2);
    }


    public record ServiceClientKey(String serviceName, String clientId) {
    }

    public record ProviderResponseKey(String providerId, String providerRspCode) {

    }

}
