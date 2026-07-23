//package com.bbl.cache.template;
//
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.GroupedCache;
//import com.bbl.common.cache.temp.support.ViewFactory;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Serviceproviderprofile;
//import jakarta.enterprise.context.ApplicationScoped;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//import java.util.Map;
//
//
///**
// * Example implementation of {@link AygwDomainCacheTemplate}.
// *
// * <p>
// * The template provides access to all standard domain caches such as:
// * </p>
// *
// * <ul>
// *     <li>CompProfileCache</li>
// *     <li>PropertiesCache</li>
// *     <li>ResponseMappingCache</li>
// *     <li>ServiceProviderProfileCache</li>
// *     <li>ServiceClientProfileCache</li>
// *     <li>AnygwServiceProfileCache</li>
// * </ul>
// *
// * <p>
// * Consumers may directly use these cache instances and initialize
// * only the cache types required by their application.
// * </p>
// *
// * <pre>
// * getCompProfileCache().initDoubleKeyCache();
// * getPropertiesCache().initDoubleKeyCache();
// * getResponseMappingCache().initDoubleKeyCache();
// * </pre>
// *
// * <h3>Custom Cache</h3>
// *
// * <p>
// * If a use case requires a dedicated cache structure that is not provided
// * by the framework, developers may create their own cache using
// * {@link CacheFacade}. Custom caches should only be introduced when:
// * </p>
// *
// * <ul>
// *     <li>The data is frequently queried.</li>
// *     <li>The grouping/transformation is expensive to regenerate.</li>
// *     <li>The derived data requires its own refresh lifecycle.</li>
// * </ul>
// *
// * <h3>ViewFactory Preferred for Temporary Views</h3>
// *
// * <p>
// * For task-specific projections, reporting, filtering, sorting,
// * temporary grouping, or one-off data transformations, prefer
// * {@link ViewFactory} over creating an additional cache.
// * </p>
// *
// * <p>
// * ViewFactory operates on existing cache data and avoids introducing
// * extra cache lifecycle management, memory consumption, and reload
// * responsibilities.
// * </p>
// *
// * <pre>
// * ViewFactory.filteredView(...);
// * ViewFactory.groupedView(...);
// * ViewFactory.sortedView(...);
// * ViewFactory.listView(...);
// * </pre>
// *
// * <p>
// * As a general guideline:
// * </p>
// *
// * <ul>
// *     <li>Frequently reused data -> Create a dedicated cache.</li>
// *     <li>Temporary or task-oriented data -> Use ViewFactory.</li>
// * </ul>
// */
//@ApplicationScoped
//public class CacheManager extends AygwDomainCacheTemplate {
//
//    private static final Logger log = LogManager.getLogger();
//
//    // Optional example of a dedicated task cache.
//// Use a custom cache only when the grouping is frequently reused
//// and worth keeping in memory.
//    private final CacheFacade taskCashFacade = new CacheFacade("taskCache", log);
//    private final GroupedCache<String, Serviceproviderprofile> retryGroup = taskCashFacade.groupedCache("retryGroup");
//
//
//    @Override
//    protected List<String> serviceNameList() {
//        return List.of("serviceOne", "serviceTwo");
//    }
//
//    @Override
//    protected List<String> categoryList() {
//        return List.of();
//    }
//
//    @Override
//    protected String productCode() {
//        return "";
//    }
//
//    public void initRetryServiceGroup() {
//        retryGroup.load(getServiceProviderProfileCache().getQuery(), f -> String.valueOf(f.getSndrqtRetry()));
//    }
//
//    public Map<String, List<Serviceproviderprofile>> getRetryServiceGroupCache() {
//        return retryGroup.asMap();
//    }
//
//    /**
//     * Example of creating a temporary view from an existing cache.
//     *
//     * <p>
//     * No additional cache instance is created.
//     * The view is derived from the already loaded
//     * ServiceProviderProfile cache.
//     * </p>
//     *
//     * <p>
//     * Prefer this approach when the result is only required for a
//     * specific task or business operation and does not justify
//     * maintaining a dedicated cache.
//     * </p>
//     */
//    public List<Serviceproviderprofile> getRetryServiceProvideList() {
//        return ViewFactory.filteredViewOfDoubleKey(getServiceProviderProfileCache().getDoubleKeyCache().asMap(), p -> p.getSndrqtRetry() == 'Y');
//    }
//
//
//}
