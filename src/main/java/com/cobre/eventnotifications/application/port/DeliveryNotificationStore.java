package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Optional;

/**
 * Narrow, internal outbound port used ONLY by the delivery engine: it loads a notification by id
 * without a client identity and persists the outcome. It is deliberately separate from the
 * client-facing {@link NotificationRepository} so that no tenant-less read is ever reachable from the
 * public API.
 */
public interface DeliveryNotificationStore {

    Optional<Notification> findForDelivery(EventId id);

    void save(Notification notification);
}
