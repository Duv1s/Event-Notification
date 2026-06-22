package com.cobre.eventnotifications.infrastructure.persistence;

import com.cobre.eventnotifications.application.port.Cursor;
import com.cobre.eventnotifications.application.port.DeliveryNotificationLookup;
import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory reference implementation of the notification ports. The production target
 * replaces it with a JPA/PostgreSQL adapter behind the same ports.
 */
@Repository
public class InMemoryNotificationRepository implements NotificationRepository, DeliveryNotificationLookup {

    private static final int MAX_PAGE_SIZE = 100;

    // occurred_at DESC, then id DESC (tiebreaker) — the keyset order.
    private static final Comparator<Notification> KEYSET_DESC = Comparator.comparing(Notification::occurredAt)
            .thenComparing(notification -> notification.id().value())
            .reversed();

    private final Map<EventId, Notification> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Notification> findByIdAndClientId(EventId id, ClientId clientId) {
        Notification notification = store.get(id);
        return notification != null && notification.clientId().equals(clientId)
                ? Optional.of(notification)
                : Optional.empty();
    }

    @Override
    public Optional<Notification> findForDelivery(EventId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public NotificationPage search(NotificationQuery query) {
        int limit = Math.min(query.pageSize(), MAX_PAGE_SIZE);

        List<Notification> matched = store.values().stream()
                .filter(n -> n.clientId().equals(query.clientId()))
                .filter(n -> withinRange(n.occurredAt(), query.occurredFrom(), query.occurredTo()))
                .filter(n -> query.statuses().isEmpty() || query.statuses().contains(n.deliveryStatus()))
                .sorted(KEYSET_DESC)
                .toList();

        List<Notification> afterCursor = query.cursor() == null
                ? matched
                : matched.stream().filter(n -> isAfter(n, query.cursor())).toList();

        List<Notification> page = afterCursor.stream().limit(limit).toList();
        Cursor next = afterCursor.size() > limit ? cursorOf(page.getLast()) : null;
        return new NotificationPage(page, next);
    }

    @Override
    public void save(Notification notification) {
        store.put(notification.id(), notification);
    }

    @Override
    public boolean claimForReplay(EventId id, ClientId clientId) {
        AtomicBoolean claimed = new AtomicBoolean(false);
        store.compute(id, (key, stored) -> {
            if (stored != null && stored.clientId().equals(clientId) && stored.canBeReplayed()) {
                stored.replay(); // FAILED -> DELIVERING
                claimed.set(true);
            }
            return stored;
        });
        return claimed.get();
    }

    private static boolean withinRange(Instant occurredAt, Instant from, Instant to) {
        if (from != null && occurredAt.isBefore(from)) {
            return false; // from is inclusive
        }
        return to == null || occurredAt.isBefore(to); // to is exclusive
    }

    private static boolean isAfter(Notification notification, Cursor cursor) {
        int byTime = notification.occurredAt().compareTo(cursor.occurredAt());
        if (byTime != 0) {
            return byTime < 0; // DESC: an earlier occurredAt comes after the cursor
        }
        return notification.id().value().compareTo(cursor.id().value()) < 0; // DESC tiebreaker
    }

    private static Cursor cursorOf(Notification notification) {
        return new Cursor(notification.occurredAt(), notification.id());
    }
}
