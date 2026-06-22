package com.cobre.eventnotifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cobre.eventnotifications.application.port.DeliveryOutcome;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.application.port.WebhookClient;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.Notification;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliverNotificationTest {

    private static final Instant CLOCK_INSTANT = Instant.parse("2024-03-15T10:00:00Z");

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final WebhookClient webhookClient = mock(WebhookClient.class);
    private final Clock clock = Clock.fixed(CLOCK_INSTANT, ZoneOffset.UTC);
    private final DeliverNotification useCase =
            new DeliverNotification(notifications, subscriptions, webhookClient, clock);

    private Notification arrangePendingDelivery() {
        Notification notification = Fixtures.pending();
        when(notifications.findForDelivery(Fixtures.EVENT_ID)).thenReturn(Optional.of(notification));
        when(subscriptions.findById(Fixtures.SUBSCRIPTION_ID)).thenReturn(Optional.of(Fixtures.activeSubscription()));
        return notification;
    }

    @Test
    void successTransitionsToCompletedAndRecordsAttempt() {
        Notification notification = arrangePendingDelivery();
        when(webhookClient.send(any(), any())).thenReturn(DeliveryOutcome.success(200));

        useCase.handle(Fixtures.EVENT_ID);

        assertEquals(DeliveryStatus.COMPLETED, notification.deliveryStatus());
        assertEquals(1, notification.attemptCount());
        verify(notifications).save(notification);
    }

    @Test
    void retryableFailureTransitionsToRetrying() {
        Notification notification = arrangePendingDelivery();
        when(webhookClient.send(any(), any())).thenReturn(DeliveryOutcome.retryableFailure(503, "unavailable"));

        useCase.handle(Fixtures.EVENT_ID);

        assertEquals(DeliveryStatus.RETRYING, notification.deliveryStatus());
        assertEquals(1, notification.attemptCount());
    }

    @Test
    void permanentFailureTransitionsToFailed() {
        Notification notification = arrangePendingDelivery();
        when(webhookClient.send(any(), any())).thenReturn(DeliveryOutcome.permanentFailure(400, "bad request"));

        useCase.handle(Fixtures.EVENT_ID);

        assertEquals(DeliveryStatus.FAILED, notification.deliveryStatus());
    }

    @Test
    void recordsAttemptWithClockTimeAndSubscriptionUrl() {
        Notification notification = arrangePendingDelivery();
        when(webhookClient.send(any(), any())).thenReturn(DeliveryOutcome.success(200));

        useCase.handle(Fixtures.EVENT_ID);

        DeliveryAttempt attempt = notification.attempts().getFirst();
        assertEquals(CLOCK_INSTANT, attempt.attemptedAt());
        assertEquals(Fixtures.URL, attempt.urlUsed());
        assertEquals(200, attempt.httpStatus());
    }
}
