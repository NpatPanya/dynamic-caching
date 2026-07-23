package com.bbl.cache.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistryKeyTest {

    @Test
    void mapKeyCreatesRecursivelyImmutableSnapshot() {
        RegistryKey<Map<String, Map<Long, String>>> key =
                RegistryKey.nestedMap("settings", String.class, Long.class, String.class);
        Map<Long, String> inner = new LinkedHashMap<>();
        inner.put(1L, "one");
        Map<String, Map<Long, String>> source = new LinkedHashMap<>();
        source.put("numbers", inner);

        Map<String, Map<Long, String>> snapshot = key.snapshot(source);
        inner.put(2L, "two");
        source.put("other", Map.of());

        assertEquals(Map.of("numbers", Map.of(1L, "one")), snapshot);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.get("numbers").put(2L, "two"));
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("other", Map.of()));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listKeyRejectsAnElementOfTheWrongRuntimeType() {
        RegistryKey<List<String>> key = RegistryKey.list("strings", String.class);
        List raw = new ArrayList();
        raw.add("valid");
        raw.add(42);

        assertThrows(IllegalArgumentException.class, () -> key.snapshot(raw));
    }

    @Test
    void keysRejectBlankNamesAndCollectionClassesInValueFactory() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistryKey.value(" ", String.class));
        assertThrows(IllegalArgumentException.class,
                () -> RegistryKey.value("list", List.class));
    }
}
