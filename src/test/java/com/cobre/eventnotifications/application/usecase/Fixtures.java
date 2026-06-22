package com.cobre.eventnotifications.application.usecase;

import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryResult;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.EventType;
import com.cobre.eventnotifications.domain.EventTypeFilter;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.SubscriptionId;
import com.cobre.eventnotifications.domain.SubscriptionState;
import com.cobre.eventnotifications.domain.WebhookUrl;
import java.time.Instant;
import java.util.List;

/** Shared builders for the application use-case tests. */
final class Fixtures {

    static final EventId EVENT_ID = new EventId("EVT001");
    static final ClientId CLIENT_ID = new ClientId("CLIENT001");
    static final SubscriptionId SUBSCRIPTION_ID = new SubscriptionId("SUB001");
    static final EventType EVENT_TYPE = new EventType("credit_transfer");
    static final WebhookUrl URL = new WebhookUrl("https://example.com/hook");
    static final Instant OCCURRED_AT = Instant.parse("2024-03-15T09:30:22Z");

    private Fixtures() {}

    static Notification notification(DeliveryStatus status, List<DeliveryAttempt> attempts) {
        return new Notification(
                EVENT_ID, CLIENT_ID, SUBSCRIPTION_ID, EVENT_TYPE, "payment received", OCCURRED_AT, status, attempts);
    }

    static Notification pending() {
        return notification(DeliveryStatus.PENDING, List.of());
    }

    static Notification failed() {
        return notification(
                DeliveryStatus.FAILED,
                List.of(new DeliveryAttempt(1, OCCURRED_AT, URL, 500, DeliveryResult.FAILURE, "boom")));
    }

    static Notification completed() {
        return notification(
                DeliveryStatus.COMPLETED,
                List.of(new DeliveryAttempt(1, OCCURRED_AT, URL, 200, DeliveryResult.SUCCESS, null)));
    }

    static Subscription subscription(EventTypeFilter filter, SubscriptionState state) {
        return new Subscription(SUBSCRIPTION_ID, CLIENT_ID, filter, URL, "demo-secret", state);
    }

    static Subscription activeSubscription() {
        return subscription(EventTypeFilter.all(), SubscriptionState.ACTIVE);
    }
}
