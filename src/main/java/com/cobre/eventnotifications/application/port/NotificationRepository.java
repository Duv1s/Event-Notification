package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Optional;

/** Outbound port for reading and persisting notifications. */
public interface NotificationRepository {

    /**
     * Client-facing lookup. ALWAYS tenant-scoped: there is intentionally no {@code findById} without a
     * {@link ClientId}, so a cross-tenant read is structurally impossible from the API.
     */
    Optional<Notification> findByIdAndClientId(EventId id, ClientId clientId);

    /**
     * Internal, system-scoped lookup used ONLY by the delivery engine (never reachable from the client
     * API). The delivery worker is a trusted internal actor and does not carry a client identity.
     */
    Optional<Notification> findForDelivery(EventId id);

    /** Keyset-paginated, tenant-scoped search. */
    NotificationPage search(NotificationQuery query);

    /** Persists state changes of an existing notification. */
    void save(Notification notification);
}
