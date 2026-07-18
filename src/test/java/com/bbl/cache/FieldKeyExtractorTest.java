package com.bbl.cache;

import com.bbl.cache.fixtures.OrderEntity;
import com.bbl.cache.fixtures.TransactionEntity;
import com.bbl.cache.fixtures.TransactionId;
import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves withKeyField(...) resolves keys via reflection instead of a hand-written KeyExtractor,
 * across a record DTO, a getter-based entity, and a plain field with no accessor at all.
 */
class FieldKeyExtractorTest {

    @Test
    void recordComponent_resolvedAsKey() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder()
                .withKeyField("id")
                .withLoader(() -> List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")))
                .buildAndLoad();

        assertEquals(2, cache.size());
        assertEquals("a@example.com", cache.getOrThrow("1").email());
    }

    @Test
    void getterBasedEntity_keyedByCustomerId_oneEntryPerElement() {
        List<OrderEntity> orders = List.of(
                new OrderEntity("o1", "1"),
                new OrderEntity("o2", "2"),
                new OrderEntity("o3", "3"),
                new OrderEntity("o4", "4"),
                new OrderEntity("o5", "5"));

        Cache<OrderEntity> cache = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("customerId")
                .withLoader(() -> orders)
                .buildAndLoad();

        assertEquals(orders.size(), cache.size());
        assertEquals("o3", cache.getOrThrow("3").getOrderId());
    }


    @Test
    void getEmbedField_key(){
        List<TransactionEntity> txn =  List.of(
                new TransactionEntity(new TransactionId("11","11212"),"ss"),
                new TransactionEntity(new TransactionId("2222","23213"),"aaa")
        );

        Cache<TransactionEntity> cache = CacheBuilder.<TransactionEntity>newBuilder().withKeyField("orderStatus").withLoader(()->txn).buildAndLoad();

        assertEquals(txn.size(), cache.size());
        assertEquals("ss",cache.getOrThrow("11").getTxnDetails());
        System.out.println(cache.asMap().getClass().getName());
        System.out.println(cache);
        System.out.println(cache.asMap());

    }

    @Test
    void getterBasedEntity_keyedByOrderId_insteadOfCustomerId() {
        List<OrderEntity> orders = List.of(new OrderEntity("o1", "c1"), new OrderEntity("o2", "c2"));

        Cache<OrderEntity> cache = CacheBuilder.<OrderEntity>newBuilder()
                .withKeyField("orderId")
                .withLoader(() -> orders)
                .buildAndLoad();

        assertEquals(2, cache.size());
        assertEquals("c2", cache.getOrThrow("o2").getCustomerId());
    }

    @Test
    void unknownFieldName_throwsCacheConfigurationException() {
        Cache<OrderEntity> cache = CacheBuilder.<OrderEntity>newBuilder().build();

        assertThrows(CacheConfigurationException.class,
                () -> cache.load(() -> List.of(new OrderEntity("o1", "c1")), FieldKeyExtractor.of("doesNotExist")));
    }

    @Test
    void fieldWithNoPublicGetter_fallsBackToDirectFieldAccess() {
        class Internal {
            @SuppressWarnings("unused")
            private final String code = "xyz";
        }

        Internal value = new Internal();
        String key = FieldKeyExtractor.<Internal>of("code").extractKey(value);

        assertEquals("xyz", key);
    }
}
