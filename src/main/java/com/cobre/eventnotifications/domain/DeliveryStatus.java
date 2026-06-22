package com.cobre.eventnotifications.domain;

/**
 * Internal delivery lifecycle of a {@link Notification}.
 *
 * <p>These are the INTERNAL states only. The mapping to the public API states ({@code PENDING},
 * {@code IN_PROGRESS}, {@code COMPLETED}, {@code FAILED}) is a DTO concern and lives in the web layer,
 * not in the domain.
 *
 * <p>Allowed transitions:
 *
 * <pre>
 *   PENDING    -&gt; DELIVERING
 *   DELIVERING -&gt; COMPLETED | RETRYING | FAILED
 *   RETRYING   -&gt; DELIVERING | FAILED
 *   FAILED     -&gt; DELIVERING            (replay)
 *   COMPLETED  -&gt; (terminal)
 * </pre>
 */
public enum DeliveryStatus {
    PENDING,
    DELIVERING,
    RETRYING,
    COMPLETED,
    FAILED;

    /** Only a definitively failed notification (retries exhausted) can be replayed. */
    public boolean canBeReplayed() {
        return this == FAILED;
    }

    public boolean canTransitionTo(DeliveryStatus target) {
        return switch (this) {
            case PENDING, FAILED -> target == DELIVERING;
            case DELIVERING -> target == COMPLETED || target == RETRYING || target == FAILED;
            case RETRYING -> target == DELIVERING || target == FAILED;
            case COMPLETED -> false;
        };
    }
}
