package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WebhookUrlTest {

    @Test
    void acceptsHttpsUrl() {
        WebhookUrl url = new WebhookUrl("https://client.example.com/webhooks/notifications");
        assertEquals("client.example.com", url.toUri().getHost());
    }

    @Test
    void rejectsHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("http://client.example.com/webhooks"));
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("ftp://client.example.com/x"));
    }

    @Test
    void rejectsSyntacticallyInvalidUri() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("ht tp://bad uri"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("   "));
    }

    @Test
    void rejectsMissingHost() {
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("https:///path-only"));
    }
}
