package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

    @Test
    void idsRejectBlankOrNull() {
        assertThrows(IllegalArgumentException.class, () -> new ClientId(" "));
        assertThrows(IllegalArgumentException.class, () -> new EventId(""));
        assertThrows(IllegalArgumentException.class, () -> new SubscriptionId(null));
        assertThrows(IllegalArgumentException.class, () -> new EventType("  "));
    }

    @Test
    void idsExposeRawValueInToString() {
        assertEquals("CLIENT001", new ClientId("CLIENT001").toString());
        assertEquals("EVT001", new EventId("EVT001").toString());
        assertEquals("SUB001", new SubscriptionId("SUB001").toString());
        assertEquals("credit_transfer", new EventType("credit_transfer").toString());
    }

    @Test
    void eventTypeFilterRejectsEmptyOrBlankPatterns() {
        assertThrows(IllegalArgumentException.class, () -> new EventTypeFilter(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EventTypeFilter(List.of("ok", " ")));
    }
}
