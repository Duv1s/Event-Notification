package com.cobre.eventnotifications.domain;

/** Thrown when a delivery status transition is not allowed by the state machine. */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(EventId id, DeliveryStatus from, DeliveryStatus to) {
        super("Invalid delivery status transition for notification " + id + ": " + from + " -> " + to);
    }
}
