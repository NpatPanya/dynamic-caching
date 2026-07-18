package com.bbl.cache;

import com.bbl.cache.fixtures.UserDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheReloadTest {

    @Test
    void reload_clearsStaleEntries_andRepopulatesFromShrunkDataset() {
        Cache<UserDto> cache = CacheBuilder.<UserDto>newBuilder()
                .withKeyExtractor(UserDto::id)
                .withLoader(() -> List.of(new UserDto("1", "a@example.com"), new UserDto("2", "b@example.com")))
                .buildAndLoad();

        assertEquals(2, cache.size());

        cache.reload(() -> List.of(new UserDto("2", "b2@example.com")), UserDto::id);

        assertEquals(1, cache.size());
        assertFalse(cache.containsKey("1"));
        assertTrue(cache.containsKey("2"));
        assertEquals("b2@example.com", cache.getOrThrow("2").email());
    }
}
