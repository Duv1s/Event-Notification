package com.cobre.eventnotifications.application.port;

/**
 * Result of a single webhook delivery attempt, as reported by the {@link WebhookClient} adapter.
 *
 * @param success whether the endpoint accepted the delivery (HTTP 2xx)
 * @param httpStatus the HTTP status returned, or {@code null} for network/connection errors
 * @param retryable whether a failure is worth retrying (5xx/429/timeout) vs. permanent (4xx/TLS/SSRF)
 * @param error a truncated, PII-free error description, or {@code null} on success
 */
public record DeliveryOutcome(boolean success, Integer httpStatus, boolean retryable, String error) {

    public DeliveryOutcome {
        if (success && retryable) {
            throw new IllegalArgumentException("a successful outcome cannot be retryable");
        }
    }

    public static DeliveryOutcome success(int httpStatus) {
        return new DeliveryOutcome(true, httpStatus, false, null);
    }

    public static DeliveryOutcome retryableFailure(Integer httpStatus, String error) {
        return new DeliveryOutcome(false, httpStatus, true, error);
    }

    public static DeliveryOutcome permanentFailure(Integer httpStatus, String error) {
        return new DeliveryOutcome(false, httpStatus, false, error);
    }
}
