package com.cobre.eventnotifications.domain;

/** Lifecycle state of a {@link Subscription}. Only {@code ACTIVE} subscriptions receive deliveries. */
public enum SubscriptionState {
    ACTIVE,
    INACTIVE,
    PAUSED
}
