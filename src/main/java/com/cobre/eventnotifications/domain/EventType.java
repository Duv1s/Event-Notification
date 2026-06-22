package com.cobre.eventnotifications.domain;

/** Type of the platform event, e.g. {@code credit_transfer} or {@code debit_purchase}. */
public record EventType(String value) {

    public EventType {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
