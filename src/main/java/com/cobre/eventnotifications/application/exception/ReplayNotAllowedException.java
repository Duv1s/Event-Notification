package com.cobre.eventnotifications.application.exception;

import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;

/** Replay was requested but the notification is not in a replayable (FAILED) state. Maps to HTTP 409. */
public class ReplayNotAllowedException extends ApplicationException {

    public ReplayNotAllowedException(EventId id, DeliveryStatus status) {
        super("Notification " + id + " cannot be replayed from status " + status + " (only FAILED is replayable)");
    }
}
