package com.cobre.eventnotifications.application.exception;

import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.SubscriptionId;

/**
 * The subscription that originated the notification is no longer eligible for (re)delivery: it is not
 * active, no longer covers the event type, or no longer exists. Maps to HTTP 409.
 */
public class SubscriptionNotEligibleException extends ApplicationException {

    public SubscriptionNotEligibleException(EventId id, SubscriptionId subscriptionId) {
        super("Subscription " + subscriptionId + " for notification " + id
                + " is not active or no longer covers the event type");
    }
}
