package com.cobre.eventnotifications.domain;

/** Identifier of a {@link Subscription}. */
public record SubscriptionId(String value) {

    public SubscriptionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
