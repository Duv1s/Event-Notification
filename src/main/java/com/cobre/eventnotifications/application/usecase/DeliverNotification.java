package com.cobre.eventnotifications.application.usecase;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.exception.SubscriptionNotEligibleException;
import com.cobre.eventnotifications.application.port.DeliveryNotificationLookup;
import com.cobre.eventnotifications.application.port.DeliveryOutcome;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.application.port.WebhookClient;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryResult;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import java.time.Clock;
import java.util.Objects;

/**
 * Performs ONE webhook delivery attempt for a notification and records the result. This is the
 * internal, system-scoped delivery path (no tenant identity); it is triggered asynchronously by the
 * {@code DeliveryDispatcher}. It reads through the narrow {@link DeliveryNotificationLookup} and
 * persists the outcome via {@link NotificationRepository#save}.
 *
 * <p>The retry loop with backoff (Resilience4j) is added in the webhook adapter phase; here a single
 * attempt is made and the notification transitions to COMPLETED / RETRYING / FAILED accordingly.
 */
public class DeliverNotification {

    private final DeliveryNotificationLookup lookup;
    private final NotificationRepository notifications;
    private final SubscriptionRepository subscriptions;
    private final WebhookClient webhookClient;
    private final Clock clock;

    public DeliverNotification(
            DeliveryNotificationLookup lookup,
            NotificationRepository notifications,
            SubscriptionRepository subscriptions,
            WebhookClient webhookClient,
            Clock clock) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.webhookClient = Objects.requireNonNull(webhookClient, "webhookClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void handle(EventId id) {
        Notification notification = lookup.findForDelivery(id).orElseThrow(() -> new NotificationNotFoundException(id));
        Subscription subscription = subscriptions
                .findById(notification.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotEligibleException(id, notification.subscriptionId()));

        // The replay path already claimed the notification (FAILED -> DELIVERING); only move it into
        // DELIVERING when it is starting fresh (PENDING) or retrying (RETRYING).
        DeliveryStatus status = notification.deliveryStatus();
        if (status == DeliveryStatus.PENDING || status == DeliveryStatus.RETRYING) {
            notification.beginDelivery();
        }

        DeliveryOutcome outcome = webhookClient.send(notification, subscription);
        DeliveryAttempt attempt = toAttempt(notification, subscription, outcome);
        if (outcome.success()) {
            notification.recordSuccess(attempt);
        } else if (outcome.retryable()) {
            notification.recordRetryableFailure(attempt);
        } else {
            notification.recordPermanentFailure(attempt);
        }

        notifications.save(notification);
    }

    private DeliveryAttempt toAttempt(Notification notification, Subscription subscription, DeliveryOutcome outcome) {
        int attemptNumber = notification.attemptCount() + 1;
        DeliveryResult result = outcome.success() ? DeliveryResult.SUCCESS : DeliveryResult.FAILURE;
        return new DeliveryAttempt(
                attemptNumber, clock.instant(), subscription.url(), outcome.httpStatus(), result, outcome.error());
    }
}
