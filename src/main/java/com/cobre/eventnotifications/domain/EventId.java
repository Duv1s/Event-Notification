package com.cobre.eventnotifications.domain;

/** Identity of a {@link Notification}; equal to the {@code notification_event_id} (== {@code event_id}). */
public record EventId(String value) {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
