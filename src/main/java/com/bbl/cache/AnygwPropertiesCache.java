//package com.bbl.cache;
//
//import com.bbl.common.application.port.out.persistence.AnygwPropertiesRepositoryPort;
//import com.bbl.common.cache.temp.registry.CacheFacade;
//import com.bbl.common.cache.temp.registry.DoubleKeyCache;
//import com.bbl.common.infrastructure.adapter.out.persistence.entity.Anygwproperties;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
//import java.util.List;
//
//
//public class AnygwPropertiesCache {
//
//    private static final Logger log = LogManager.getLogger();
//
//    private final CacheFacade cacheFacade = new CacheFacade(this.getClass().getName(), log);
//
//
//    private final DoubleKeyCache<String, String, String> doubleKeyCache = cacheFacade.doubleKeyCache("propertyDoubleKeyCache");
//
//    private final AnygwPropertiesRepositoryPort port;
//    private final List<String> categoryList;
//    private final String productCd;
//
//    public AnygwPropertiesCache(AnygwPropertiesRepositoryPort port, String productCode, List<String> categoryList) {
//        this.port = port;
//        this.productCd = productCode;
//        this.categoryList = categoryList;
//    }
//
//    public List<Anygwproperties> getQuery() {
//        return port.findByProductCdAndCategoryList(productCd, categoryList);
//    }
//
//    /**
//     * Loads provider profiles for the specified service names.
//     *
//     * <p>Key = productCode, categoryList
//     * <br>Value =
//     */
//    public void initDoubleKeyCache() {
//        doubleKeyCache.load(getQuery(), f -> f.getId().getCategory(), s -> s.getId().getVarName(), Anygwproperties::getVarValue);
//    }
//
//    public DoubleKeyCache<String, String, String> getDoubleKeyCache() {
//        return doubleKeyCache;
//    }
//
//}
