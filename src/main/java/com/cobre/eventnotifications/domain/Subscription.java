package com.cobre.eventnotifications.domain;

import java.util.Objects;

/**
 * A client's subscription: where and how a matching event is delivered. Assumed pre-existing (its
 * CRUD is out of scope); the delivery engine only reads it to decide whether and where to deliver.
 *
 * <p>The {@code hmacSecret} is sensitive and is never exposed through {@link #toString()}.
 */
public final class Subscription {

    private final SubscriptionId subscriptionId;
    private final ClientId clientId;
    private final EventTypeFilter eventTypeFilter;
    private final WebhookUrl url;
    private final String hmacSecret;
    private final SubscriptionState state;

    public Subscription(
            SubscriptionId subscriptionId,
            ClientId clientId,
            EventTypeFilter eventTypeFilter,
            WebhookUrl url,
            String hmacSecret,
            SubscriptionState state) {
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.eventTypeFilter = Objects.requireNonNull(eventTypeFilter, "eventTypeFilter");
        this.url = Objects.requireNonNull(url, "url");
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("hmacSecret must not be blank");
        }
        this.hmacSecret = hmacSecret;
        this.state = Objects.requireNonNull(state, "state");
    }

    public boolean isActive() {
        return state == SubscriptionState.ACTIVE;
    }

    public boolean covers(EventType eventType) {
        return eventTypeFilter.matches(eventType);
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }

    public ClientId clientId() {
        return clientId;
    }

    public EventTypeFilter eventTypeFilter() {
        return eventTypeFilter;
    }

    public WebhookUrl url() {
        return url;
    }

    public String hmacSecret() {
        return hmacSecret;
    }

    public SubscriptionState state() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Subscription other && subscriptionId.equals(other.subscriptionId);
    }

    @Override
    public int hashCode() {
        return subscriptionId.hashCode();
    }

    /** Excludes {@code hmacSecret} on purpose so the secret never leaks into logs. */
    @Override
    public String toString() {
        return "Subscription[subscriptionId=" + subscriptionId
                + ", clientId=" + clientId
                + ", eventTypeFilter=" + eventTypeFilter
                + ", url=" + url
                + ", state=" + state
                + "]";
    }
}
