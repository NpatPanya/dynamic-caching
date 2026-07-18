package com.bbl.cache;

import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheBuilderTest {

    @Test
    void buildAndLoad_withoutKeyExtractor_throws() {
        CacheBuilder<UserDto> builder = CacheBuilder.<UserDto>newBuilder()
                .withLoader(() -> List.of(new UserDto("1", "a@example.com")));

        assertThrows(CacheConfigurationException.class, builder::buildAndLoad);
    }

    @Test
    void buildAndLoad_withoutLoader_throws() {
        CacheBuilder<UserDto> builder = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id);

        assertThrows(CacheConfigurationException.class, builder::buildAndLoad);
    }

    @Test
    void build_returnsEmptyCache_regardlessOfExtractorOrLoader() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder().build();

        assertTrue(cache.isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    void buildAndLoad_populatesCacheImmediately() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")))
                .buildAndLoad();

        assertEquals(2, cache.size());
        assertEquals("a@example.com", cache.getOrThrow("1").email());
    }

    @Test
    void withInitialCapacity_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CacheBuilder.<UserDto>newBuilder().withInitialCapacity(-1));
    }
}
