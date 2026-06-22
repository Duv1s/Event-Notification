package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryAttemptTest {

    private static final WebhookUrl URL = new WebhookUrl("https://example.com/hook");
    private static final Instant NOW = Instant.parse("2024-03-15T09:30:22Z");

    @Test
    void buildsValidAttempt() {
        DeliveryAttempt attempt = new DeliveryAttempt(1, NOW, URL, 200, DeliveryResult.SUCCESS, null);
        assertEquals(1, attempt.attemptNumber());
        assertEquals(200, attempt.httpStatus());
        assertEquals(DeliveryResult.SUCCESS, attempt.result());
        assertNull(attempt.error());
    }

    @Test
    void allowsNullHttpStatusForNetworkErrors() {
        DeliveryAttempt attempt = new DeliveryAttempt(2, NOW, URL, null, DeliveryResult.FAILURE, "connection reset");
        assertNull(attempt.httpStatus());
        assertEquals(DeliveryResult.FAILURE, attempt.result());
    }

    @Test
    void truncatesOverlongError() {
        DeliveryAttempt attempt = new DeliveryAttempt(1, NOW, URL, 500, DeliveryResult.FAILURE, "x".repeat(5000));
        assertEquals(1000, attempt.error().length());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryAttempt(0, NOW, URL, 200, DeliveryResult.SUCCESS, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryAttempt(1, null, URL, 200, DeliveryResult.SUCCESS, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeliveryAttempt(1, NOW, null, 200, DeliveryResult.SUCCESS, null));
        assertThrows(IllegalArgumentException.class, () -> new DeliveryAttempt(1, NOW, URL, 200, null, null));
    }
}
