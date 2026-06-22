package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.domain.DeliveryStatus;
import java.util.Set;

/**
 * The delivery status as exposed by the public API. Internal {@code DELIVERING} and {@code RETRYING}
 * both surface as {@code IN_PROGRESS}, so the retry mechanics can evolve without breaking the
 * contract. The mapping lives here, at the edge.
 */
public enum PublicDeliveryStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED;

    public static PublicDeliveryStatus fromInternal(DeliveryStatus status) {
        return switch (status) {
            case PENDING -> PENDING;
            case DELIVERING, RETRYING -> IN_PROGRESS;
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
        };
    }

    public Set<DeliveryStatus> toInternal() {
        return switch (this) {
            case PENDING -> Set.of(DeliveryStatus.PENDING);
            case IN_PROGRESS -> Set.of(DeliveryStatus.DELIVERING, DeliveryStatus.RETRYING);
            case COMPLETED -> Set.of(DeliveryStatus.COMPLETED);
            case FAILED -> Set.of(DeliveryStatus.FAILED);
        };
    }
}
