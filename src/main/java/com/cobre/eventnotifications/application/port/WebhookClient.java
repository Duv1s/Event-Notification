package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;

/** Outbound port for delivering a notification to a subscription's webhook endpoint. */
public interface WebhookClient {

    /**
     * Performs ONE HTTP delivery attempt and reports the outcome. No retry/backoff and no SSRF guard
     * here: those live in the adapter (added in a later phase). Implementations must not throw on a
     * delivery failure; they return a {@link DeliveryOutcome} describing it.
     */
    DeliveryOutcome send(Notification notification, Subscription subscription);
}
