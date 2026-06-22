package com.cobre.eventnotifications.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobre.eventnotifications.domain.DeliveryStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicDeliveryStatusTest {

    @Test
    void mapsInternalStatusToPublic() {
        assertEquals(PublicDeliveryStatus.PENDING, PublicDeliveryStatus.fromInternal(DeliveryStatus.PENDING));
        assertEquals(PublicDeliveryStatus.IN_PROGRESS, PublicDeliveryStatus.fromInternal(DeliveryStatus.DELIVERING));
        assertEquals(PublicDeliveryStatus.IN_PROGRESS, PublicDeliveryStatus.fromInternal(DeliveryStatus.RETRYING));
        assertEquals(PublicDeliveryStatus.COMPLETED, PublicDeliveryStatus.fromInternal(DeliveryStatus.COMPLETED));
        assertEquals(PublicDeliveryStatus.FAILED, PublicDeliveryStatus.fromInternal(DeliveryStatus.FAILED));
    }

    @Test
    void inProgressExpandsToBothInternalStates() {
        assertEquals(
                Set.of(DeliveryStatus.DELIVERING, DeliveryStatus.RETRYING),
                PublicDeliveryStatus.IN_PROGRESS.toInternal());
    }
}
