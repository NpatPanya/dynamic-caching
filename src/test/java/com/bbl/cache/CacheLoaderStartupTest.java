package com.bbl.cache;

import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Simulates the two supported startup patterns: buildAndLoad(), and build()+load() called separately. */
class CacheLoaderStartupTest {

    private static final List<UserDto> SEED = List.of(
            new UserDto("1", "a@example.com"),
            new UserDto("2", "b@example.com"),
            new UserDto("3", "c@example.com"));

    @Test
    void buildAndLoad_populatesAllElementsBeforeFirstRead() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> SEED)
                .buildAndLoad();

        assertEquals(SEED.size(), cache.size());
        for (UserDto dto : SEED) {
            assertEquals(dto, cache.getOrThrow(dto.id()));
        }
    }

    @Test
    void build_thenLoad_explicitly_mirrorsPostConstructStyleStartup() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder().build();

        cache.load(() -> SEED, UserDto::id);

        assertEquals(SEED.size(), cache.size());
        assertEquals("b@example.com", cache.getOrThrow("2").email());
    }
}
