package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubscriptionTest {

    private static Subscription subscription(EventTypeFilter filter, SubscriptionState state) {
        return new Subscription(
                new SubscriptionId("SUB001"),
                new ClientId("CLIENT001"),
                filter,
                new WebhookUrl("https://example.com/hook"),
                "demo-secret",
                state);
    }

    @Test
    void coversEverythingWithAll() {
        Subscription subscription = subscription(EventTypeFilter.all(), SubscriptionState.ACTIVE);
        assertTrue(subscription.covers(new EventType("credit_transfer")));
        assertTrue(subscription.covers(new EventType("debit_purchase")));
    }

    @Test
    void coversExplicitListOnly() {
        Subscription subscription = subscription(
                new EventTypeFilter(List.of("credit_transfer", "debit_purchase")), SubscriptionState.ACTIVE);
        assertTrue(subscription.covers(new EventType("credit_transfer")));
        assertTrue(subscription.covers(new EventType("debit_purchase")));
        assertFalse(subscription.covers(new EventType("credit_refund")));
    }

    @Test
    void coversPrefixPattern() {
        Subscription subscription = subscription(new EventTypeFilter(List.of("credit_*")), SubscriptionState.ACTIVE);
        assertTrue(subscription.covers(new EventType("credit_transfer")));
        assertTrue(subscription.covers(new EventType("credit_refund")));
        assertFalse(subscription.covers(new EventType("debit_purchase")));
    }

    @Test
    void isActiveOnlyForActiveState() {
        assertTrue(subscription(EventTypeFilter.all(), SubscriptionState.ACTIVE).isActive());
        assertFalse(
                subscription(EventTypeFilter.all(), SubscriptionState.PAUSED).isActive());
        assertFalse(
                subscription(EventTypeFilter.all(), SubscriptionState.INACTIVE).isActive());
    }

    @Test
    void toStringNeverLeaksHmacSecret() {
        Subscription subscription = new Subscription(
                new SubscriptionId("SUB001"),
                new ClientId("CLIENT001"),
                EventTypeFilter.all(),
                new WebhookUrl("https://example.com/hook"),
                "top-secret-value",
                SubscriptionState.ACTIVE);
        assertFalse(subscription.toString().contains("top-secret-value"));
    }
}
