package com.cobre.eventnotifications.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeliveryStatusTest {

    @Test
    void allowsValidTransitions() {
        assertTrue(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.DELIVERING));
        assertTrue(DeliveryStatus.DELIVERING.canTransitionTo(DeliveryStatus.COMPLETED));
        assertTrue(DeliveryStatus.DELIVERING.canTransitionTo(DeliveryStatus.RETRYING));
        assertTrue(DeliveryStatus.DELIVERING.canTransitionTo(DeliveryStatus.FAILED));
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.DELIVERING));
        assertTrue(DeliveryStatus.RETRYING.canTransitionTo(DeliveryStatus.FAILED));
        assertTrue(DeliveryStatus.FAILED.canTransitionTo(DeliveryStatus.DELIVERING));
    }

    @Test
    void rejectsInvalidTransitions() {
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.COMPLETED));
        assertFalse(DeliveryStatus.PENDING.canTransitionTo(DeliveryStatus.FAILED));
        assertFalse(DeliveryStatus.DELIVERING.canTransitionTo(DeliveryStatus.PENDING));
        assertFalse(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.DELIVERING));
        assertFalse(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.FAILED));
        assertFalse(DeliveryStatus.FAILED.canTransitionTo(DeliveryStatus.COMPLETED));
    }

    @Test
    void onlyFailedCanBeReplayed() {
        assertTrue(DeliveryStatus.FAILED.canBeReplayed());
        for (DeliveryStatus status : DeliveryStatus.values()) {
            if (status != DeliveryStatus.FAILED) {
                assertFalse(status.canBeReplayed(), status + " must not be replayable");
            }
        }
    }
}
