package com.bbl.cache.registry;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal in-memory log4j-core {@code Appender} used only by tests to
 * capture emitted {@link LogEvent}s so that TRACE-only-when-enabled
 * behavior can be asserted without scraping stdout.
 */
final class ListTestAppender extends AbstractAppender {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    ListTestAppender(String name) {
        super(name, null, null, false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    List<LogEvent> events() {
        return events;
    }

    void clear() {
        events.clear();
    }
}
