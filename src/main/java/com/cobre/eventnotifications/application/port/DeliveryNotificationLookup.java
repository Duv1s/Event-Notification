package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Optional;

/**
 * Narrow, internal outbound port used ONLY by the delivery engine to load a notification by id
 * without a client identity. It is deliberately separate from the client-facing {@link
 * NotificationRepository} so that no tenant-less read is ever reachable from the public API.
 */
public interface DeliveryNotificationLookup {

    Optional<Notification> findForDelivery(EventId id);
}
