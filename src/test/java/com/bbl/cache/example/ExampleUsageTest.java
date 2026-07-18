package com.bbl.cache.example;

import org.junit.jupiter.api.Test;

/**
 * Runs every scenario in {@link ExampleUsage} on every {@code mvn test}, so the examples can't
 * silently drift out of date as the API changes.
 */
class ExampleUsageTest {

    @Test
    void allExampleScenariosRunWithoutError() {
        ExampleUsage.main(new String[0]);
    }
}
