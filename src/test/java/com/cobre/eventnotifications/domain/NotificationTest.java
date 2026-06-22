package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final WebhookUrl URL = new WebhookUrl("https://example.com/hook");
    private static final Instant T0 = Instant.parse("2024-03-15T09:30:00Z");

    private static Notification pending() {
        return withStatus(DeliveryStatus.PENDING, List.of());
    }

    private static Notification withStatus(DeliveryStatus status, List<DeliveryAttempt> attempts) {
        return new Notification(
                new EventId("EVT001"),
                new ClientId("CLIENT001"),
                new SubscriptionId("SUB001"),
                new EventType("credit_transfer"),
                "payment received",
                Instant.parse("2024-03-15T09:30:22Z"),
                status,
                attempts);
    }

    private static DeliveryAttempt attempt(int number, DeliveryResult result, Integer http, String error) {
        return new DeliveryAttempt(number, T0.plusSeconds(number), URL, http, result, error);
    }

    @Test
    void beginDeliveryFromPending() {
        Notification notification = pending();
        notification.beginDelivery();
        assertEquals(DeliveryStatus.DELIVERING, notification.deliveryStatus());
    }

    @Test
    void successFromDeliveringCompletes() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordSuccess(attempt(1, DeliveryResult.SUCCESS, 200, null));
        assertEquals(DeliveryStatus.COMPLETED, notification.deliveryStatus());
        assertEquals(1, notification.attemptCount());
    }

    @Test
    void retryableFailureFromDeliveringRetries() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordRetryableFailure(attempt(1, DeliveryResult.FAILURE, 503, "service unavailable"));
        assertEquals(DeliveryStatus.RETRYING, notification.deliveryStatus());
    }

    @Test
    void permanentFailureFromDeliveringFails() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordPermanentFailure(attempt(1, DeliveryResult.FAILURE, 400, "bad request"));
        assertEquals(DeliveryStatus.FAILED, notification.deliveryStatus());
    }

    @Test
    void retryThenExhaustedFails() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordRetryableFailure(attempt(1, DeliveryResult.FAILURE, 503, "try later"));
        assertEquals(DeliveryStatus.RETRYING, notification.deliveryStatus());
        notification.markExhausted();
        assertEquals(DeliveryStatus.FAILED, notification.deliveryStatus());
    }

    @Test
    void replayFromFailedGoesToDelivering() {
        Notification notification =
                withStatus(DeliveryStatus.FAILED, List.of(attempt(1, DeliveryResult.FAILURE, 500, "boom")));
        assertTrue(notification.canBeReplayed());
        notification.replay();
        assertEquals(DeliveryStatus.DELIVERING, notification.deliveryStatus());
    }

    @Test
    void replayFromNonFailedThrows() {
        Notification completed =
                withStatus(DeliveryStatus.COMPLETED, List.of(attempt(1, DeliveryResult.SUCCESS, 200, null)));
        assertFalse(completed.canBeReplayed());
        assertThrows(NotReplayableException.class, completed::replay);
        assertThrows(NotReplayableException.class, pending()::replay);
    }

    @Test
    void invalidTransitionThrows() {
        Notification completed =
                withStatus(DeliveryStatus.COMPLETED, List.of(attempt(1, DeliveryResult.SUCCESS, 200, null)));
        assertThrows(InvalidStateTransitionException.class, completed::beginDelivery);
    }

    @Test
    void transitionIsValidatedBeforeRecordingAttempt() {
        Notification notification = pending(); // not DELIVERING yet
        assertThrows(
                InvalidStateTransitionException.class,
                () -> notification.recordSuccess(attempt(1, DeliveryResult.SUCCESS, 200, null)));
        assertEquals(0, notification.attemptCount());
    }

    @Test
    void derivedValuesReflectOrderedAttempts() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordRetryableFailure(attempt(1, DeliveryResult.FAILURE, 503, "first error"));
        notification.beginDelivery();
        notification.recordPermanentFailure(attempt(2, DeliveryResult.FAILURE, 500, "second error"));

        assertEquals(2, notification.attemptCount());
        assertEquals(T0.plusSeconds(2), notification.lastAttemptAt().orElseThrow());
        assertEquals("second error", notification.lastError().orElseThrow());
        assertEquals(URL, notification.lastUrl().orElseThrow());

        List<DeliveryAttempt> ordered = notification.attempts();
        assertEquals(1, ordered.get(0).attemptNumber());
        assertEquals(2, ordered.get(1).attemptNumber());
    }

    @Test
    void lastErrorIsEmptyOnSuccess() {
        Notification notification = pending();
        notification.beginDelivery();
        notification.recordSuccess(attempt(1, DeliveryResult.SUCCESS, 200, null));
        assertTrue(notification.lastError().isEmpty());
    }

    @Test
    void derivedValuesAreEmptyWithNoAttempts() {
        Notification notification = pending();
        assertEquals(0, notification.attemptCount());
        assertTrue(notification.lastAttemptAt().isEmpty());
        assertTrue(notification.lastError().isEmpty());
        assertTrue(notification.lastUrl().isEmpty());
    }

    @Test
    void attemptsSnapshotIsImmutable() {
        Notification notification =
                withStatus(DeliveryStatus.FAILED, List.of(attempt(1, DeliveryResult.FAILURE, 500, "x")));
        List<DeliveryAttempt> snapshot = notification.attempts();
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.add(attempt(2, DeliveryResult.SUCCESS, 200, null)));
    }

    @Test
    void toStringNeverLeaksContent() {
        Notification notification = new Notification(
                new EventId("EVT001"),
                new ClientId("CLIENT001"),
                new SubscriptionId("SUB001"),
                new EventType("credit_transfer"),
                "super-secret-financial-content",
                Instant.parse("2024-03-15T09:30:22Z"),
                DeliveryStatus.PENDING,
                List.of());
        assertFalse(notification.toString().contains("super-secret-financial-content"));
    }
}
