package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A tenant-scoped, keyset-paginated query over notifications.
 *
 * @param clientId the tenant (required); results are always scoped to this client
 * @param occurredFrom inclusive lower bound on {@code occurredAt}, or {@code null} for open
 * @param occurredTo exclusive upper bound on {@code occurredAt}, or {@code null} for open
 * @param statuses the public statuses to include; empty means no status filter
 * @param cursor the keyset cursor of the previous page, or {@code null} for the first page
 * @param pageSize the maximum number of items to return (must be &gt;= 1)
 */
public record NotificationQuery(
        ClientId clientId,
        Instant occurredFrom,
        Instant occurredTo,
        Set<DeliveryStatus> statuses,
        Cursor cursor,
        int pageSize) {

    public NotificationQuery {
        Objects.requireNonNull(clientId, "clientId");
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1");
        }
        if (occurredFrom != null && occurredTo != null && !occurredFrom.isBefore(occurredTo)) {
            throw new IllegalArgumentException("occurredFrom must be before occurredTo");
        }
    }
}
