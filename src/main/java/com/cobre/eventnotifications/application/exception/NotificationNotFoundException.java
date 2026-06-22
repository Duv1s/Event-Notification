package com.cobre.eventnotifications.application.exception;

import com.cobre.eventnotifications.domain.EventId;

/** The requested notification does not exist for the caller. Maps to HTTP 404. */
public class NotificationNotFoundException extends ApplicationException {

    public NotificationNotFoundException(EventId id) {
        super("Notification not found: " + id);
    }
}
