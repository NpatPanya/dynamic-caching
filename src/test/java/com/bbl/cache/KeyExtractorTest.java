package com.bbl.cache;

import com.bbl.cache.fixtures.OrderEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the library performs no reflection or naming-convention key derivation — it strictly
 * calls the supplied {@link KeyExtractor}, including composite/derived keys.
 */
class KeyExtractorTest {

    @Test
    void compositeKeyExtractor_isHonored() {
        OrderEntity order = new OrderEntity("o1", "c1");
        KeyExtractor<OrderEntity> composite = o -> o.getCustomerId() + "-" + o.getOrderId();

        Cache<OrderEntity> cache = CacheBuilder.<OrderEntity>newBuilder().build();
        cache.load(() -> List.of(order), composite);

        assertTrue(cache.containsKey("c1-o1"));
        assertEquals(order, cache.getOrThrow("c1-o1"));
    }

    @Test
    void differentExtractors_produceDifferentKeys_forSameObject() {
        OrderEntity order = new OrderEntity("o1", "c1");

        Cache<OrderEntity> byOrderId = CacheBuilder.<OrderEntity>newBuilder().build();
        byOrderId.load(() -> List.of(order), OrderEntity::getOrderId);

        Cache<OrderEntity> byCustomerId = CacheBuilder.<OrderEntity>newBuilder().build();
        byCustomerId.load(() -> List.of(order), OrderEntity::getCustomerId);

        assertTrue(byOrderId.containsKey("o1"));
        assertTrue(byCustomerId.containsKey("c1"));
        assertTrue(byOrderId.get("c1").isEmpty());
    }
}
