package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Optional;

/**
 * Client-facing outbound port (List / Get / Replay). It never exposes a read without a {@link
 * ClientId}, so a cross-tenant read is structurally impossible from the API. The internal,
 * tenant-less delivery read/write lives in {@link DeliveryNotificationStore}.
 */
public interface NotificationRepository {

    /** Tenant-scoped lookup. */
    Optional<Notification> findByIdAndClientId(EventId id, ClientId clientId);

    /** Keyset-paginated, tenant-scoped search. */
    NotificationPage search(NotificationQuery query);

    /**
     * Atomically claims a notification for replay: if (and only if) it currently belongs to {@code
     * clientId} and is in {@code FAILED}, it transitions to {@code DELIVERING} and returns {@code
     * true}; otherwise it leaves the notification untouched and returns {@code false}. This is the
     * atomic guard against concurrent double-replay.
     */
    boolean claimForReplay(EventId id, ClientId clientId);
}
