package com.bbl.cache;

import com.bbl.cache.fixtures.OrderEntity;
import com.bbl.cache.fixtures.PlainPojo;
import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves put/get round-trips for a plain object, a record-based DTO, and a JPA-entity-shaped class. */
class ConcurrentMapCacheTest {

    @Test
    void plainPojo_putThenGet_roundTrips() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();
        PlainPojo pojo = new PlainPojo("p1", "widget");

        cache.put(pojo.getId(), pojo);

        assertEquals(Optional.of(pojo), cache.get("p1"));
    }

    @Test
    void dtoRecord_putThenGet_roundTrips() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder().build();
        UserDto dto = new UserDto("u1", "a@example.com");

        cache.put(dto.id(), dto);

        assertEquals(Optional.of(dto), cache.get("u1"));
    }

    @Test
    void jpaEntityShaped_putThenGet_roundTrips() {
        Cache<OrderEntity> cache = CacheBuilder.<OrderEntity>newBuilder().build();
        OrderEntity order = new OrderEntity("o1", "c1");

        cache.put(order.getOrderId(), order);

        assertEquals(Optional.of(order), cache.get("o1"));
    }

    @Test
    void listOfJpaEntities_putThenGet_roundTrips() {
        List<OrderEntity> orderList = List.of(new OrderEntity("o1", "c1"), new OrderEntity("o2", "c2"));
        Cache<List<OrderEntity>> cache = CacheBuilder.<List<OrderEntity>>newBuilder().build();

        cache.put("orders-for-c1", orderList);

        assertEquals(Optional.of(orderList), cache.get("orders-for-c1"));
        assertEquals(2, cache.getOrThrow("orders-for-c1").size());
    }

    @Test
    void get_missingKey_returnsEmptyOptional() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();

        assertTrue(cache.get("missing").isEmpty());
    }

    @Test
    void getOrThrow_missingKey_throwsCacheMissException() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();

        assertThrows(CacheMissException.class, () -> cache.getOrThrow("missing"));
    }

    @Test
    void removeAndClear_and_containsKey() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();
        cache.put("p1", new PlainPojo("p1", "widget"));

        assertTrue(cache.containsKey("p1"));

        cache.remove("p1");
        assertFalse(cache.containsKey("p1"));

        cache.put("p2", new PlainPojo("p2", "gadget"));
        cache.clear();
        assertTrue(cache.isEmpty());
    }

    @Test
    void asMap_isUnmodifiable() {
        Cache<PlainPojo> cache = CacheBuilder.<PlainPojo>newBuilder().build();
        cache.put("p1", new PlainPojo("p1", "widget"));

        assertThrows(UnsupportedOperationException.class, () -> cache.asMap().put("p2", new PlainPojo("p2", "x")));
    }
}
