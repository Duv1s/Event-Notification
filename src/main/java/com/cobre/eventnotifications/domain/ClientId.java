package com.cobre.eventnotifications.domain;

/** Identifier of a client; the tenant / isolation unit of the system. */
public record ClientId(String value) {

    public ClientId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
