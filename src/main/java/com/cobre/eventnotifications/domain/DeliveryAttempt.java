package com.cobre.eventnotifications.domain;

import java.time.Instant;

/**
 * A single, immutable record of one delivery attempt. Part of a notification's ordered audit trail.
 *
 * @param attemptNumber 1-based position of this attempt
 * @param attemptedAt when the attempt was made
 * @param urlUsed the destination URL actually used (resolved from the subscription at attempt time)
 * @param httpStatus the HTTP status returned, or {@code null} for network/connection errors
 * @param result whether the attempt succeeded or failed
 * @param error a truncated, PII-free error description, or {@code null} on success
 */
public record DeliveryAttempt(
        int attemptNumber,
        Instant attemptedAt,
        WebhookUrl urlUsed,
        Integer httpStatus,
        DeliveryResult result,
        String error) {

    private static final int MAX_ERROR_LENGTH = 1000;

    public DeliveryAttempt {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (attemptedAt == null) {
            throw new IllegalArgumentException("attemptedAt must not be null");
        }
        if (urlUsed == null) {
            throw new IllegalArgumentException("urlUsed must not be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (error != null && error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
    }
}
