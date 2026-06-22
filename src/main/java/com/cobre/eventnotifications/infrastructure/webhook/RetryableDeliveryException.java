package com.cobre.eventnotifications.infrastructure.webhook;

import java.time.Duration;
import java.util.Optional;

/**
 * Internal signal that a delivery attempt failed transiently and should be retried. Resilience4j
 * retries on this exception and the circuit breaker counts it as a failure. Carries the HTTP status
 * (nullable for network errors) and an optional {@code Retry-After} hint.
 */
class RetryableDeliveryException extends RuntimeException {

    private final Integer httpStatus;
    private final Duration retryAfter;

    RetryableDeliveryException(Integer httpStatus, String message, Duration retryAfter) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }

    Integer httpStatus() {
        return httpStatus;
    }

    Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
