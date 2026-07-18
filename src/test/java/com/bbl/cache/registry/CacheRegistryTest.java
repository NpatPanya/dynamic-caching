package com.bbl.cache.registry;

import com.bbl.cache.Cache;
import com.bbl.cache.CacheBuilder;
import com.bbl.cache.fixtures.OrderEntity;
import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheRegistryTest {

    @Test
    void registerAndGet_twoDifferentlyTypedCaches_byName() {
        CacheRegistry registry = CacheRegistry.create();
        Cache<UserDto> users = CacheBuilder.<UserDto>newBuilder().build();
        Cache<OrderEntity> orders = CacheBuilder.<OrderEntity>newBuilder().build();


        registry.register("users", users, UserDto.class);
        registry.register("orders", orders, OrderEntity.class);



        assertEquals(users, registry.get("users", UserDto.class));
        assertEquals(orders, registry.get("orders", OrderEntity.class));

    }

    @Test
    void register_duplicateName_throws() {
        CacheRegistry registry = CacheRegistry.create();
        registry.register("users", CacheBuilder.<UserDto>newBuilder().build(), UserDto.class);

        assertThrows(CacheRegistryException.class,
                () -> registry.register("users", CacheBuilder.<UserDto>newBuilder().build(), UserDto.class));
    }

    @Test
    void get_unknownName_throws() {
        CacheRegistry registry = CacheRegistry.create();

        assertThrows(CacheRegistryException.class, () -> registry.get("missing", UserDto.class));
    }

    @Test
    void get_wrongType_throws() {
        CacheRegistry registry = CacheRegistry.create();
        registry.register("users", CacheBuilder.<UserDto>newBuilder().build(), UserDto.class);

        assertThrows(CacheRegistryException.class, () -> registry.get("users", OrderEntity.class));
    }

    @Test
    void contains_and_clear() {
        CacheRegistry registry = CacheRegistry.create();
        registry.register("users", CacheBuilder.<UserDto>newBuilder().build(), UserDto.class);

        assertTrue(registry.contains("users"));

        registry.clear();
        assertTrue(!registry.contains("users"));
    }

    @Test
    void registerListAndGetList_roundTrips() {
        CacheRegistry registry = CacheRegistry.create();
        Cache<List<UserDto>> userLists = CacheBuilder.<List<UserDto>>newBuilder().build();
        userLists.put("active", List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")));

        registry.registerList("userLists", userLists, UserDto.class);

        Cache<List<UserDto>> retrieved = registry.getList("userLists", UserDto.class);
        assertEquals(userLists, retrieved);
        assertEquals(2, retrieved.getOrThrow("active").size());
    }


    @Test
    void getList_withScalarRegisteredName_throws() {
        CacheRegistry registry = CacheRegistry.create();
        registry.register("users", CacheBuilder.<UserDto>newBuilder().build(), UserDto.class);

        assertThrows(CacheRegistryException.class, () -> registry.getList("users", UserDto.class));
    }

    @Test
    void get_withListRegisteredName_throws() {
        CacheRegistry registry = CacheRegistry.create();
        registry.registerList("userLists", CacheBuilder.<List<UserDto>>newBuilder().build(), UserDto.class);

        assertThrows(CacheRegistryException.class, () -> registry.get("userLists", UserDto.class));
    }

    @Test
    void getList_wrongElementType_throws() {
        CacheRegistry registry = CacheRegistry.create();
        registry.registerList("userLists", CacheBuilder.<List<UserDto>>newBuilder().build(), UserDto.class);

        assertThrows(CacheRegistryException.class, () -> registry.getList("userLists", OrderEntity.class));
    }
}
