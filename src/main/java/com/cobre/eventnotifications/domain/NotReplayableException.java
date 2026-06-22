package com.cobre.eventnotifications.domain;

/** Thrown when a replay is requested for a notification that is not in the {@code FAILED} state. */
public class NotReplayableException extends DomainException {

    public NotReplayableException(EventId id, DeliveryStatus status) {
        super("Notification " + id + " cannot be replayed from status " + status + " (only FAILED is replayable)");
    }
}
