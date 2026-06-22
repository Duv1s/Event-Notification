package com.cobre.eventnotifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryResult;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.EventType;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.SubscriptionId;
import com.cobre.eventnotifications.domain.WebhookUrl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InMemoryNotificationRepositoryTest {

    private static final WebhookUrl URL = new WebhookUrl("https://example.com/hook");
    private static final Instant T1 = Instant.parse("2024-03-15T09:00:00Z");
    private static final Instant T2 = Instant.parse("2024-03-15T10:00:00Z");
    private static final Instant T3 = Instant.parse("2024-03-15T11:00:00Z");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();

    @Test
    void tenantScopingAndDeliveryLookup() {
        repository.save(notification("EVT001", "CLIENT001", T1, DeliveryStatus.PENDING));

        assertTrue(repository
                .findByIdAndClientId(new EventId("EVT001"), new ClientId("CLIENT001"))
                .isPresent());
        assertTrue(repository
                .findByIdAndClientId(new EventId("EVT001"), new ClientId("CLIENT002"))
                .isEmpty());
        assertTrue(repository.findForDelivery(new EventId("EVT001")).isPresent());
    }

    @Test
    void searchFiltersByRangeAndStatusAndOrdersDesc() {
        repository.save(notification("EVT001", "CLIENT001", T1, DeliveryStatus.COMPLETED));
        repository.save(notification("EVT002", "CLIENT001", T2, DeliveryStatus.FAILED));
        repository.save(notification("EVT003", "CLIENT001", T3, DeliveryStatus.COMPLETED));
        repository.save(notification("EVT099", "CLIENT002", T2, DeliveryStatus.COMPLETED));

        assertEquals(List.of("EVT003", "EVT002", "EVT001"), ids(search("CLIENT001", null, null, Set.of())));
        assertEquals(List.of("EVT002"), ids(search("CLIENT001", null, null, Set.of(DeliveryStatus.FAILED))));
        assertEquals(List.of("EVT002"), ids(search("CLIENT001", T2, T3, Set.of()))); // [T2, T3)
        assertEquals(List.of("EVT099"), ids(search("CLIENT002", null, null, Set.of())));
    }

    @Test
    void searchPaginatesWithKeyset() {
        repository.save(notification("EVT001", "CLIENT001", T1, DeliveryStatus.COMPLETED));
        repository.save(notification("EVT002", "CLIENT001", T2, DeliveryStatus.COMPLETED));
        repository.save(notification("EVT003", "CLIENT001", T3, DeliveryStatus.COMPLETED));
        Instant t4 = Instant.parse("2024-03-15T12:00:00Z");
        Instant t5 = Instant.parse("2024-03-15T13:00:00Z");
        repository.save(notification("EVT004", "CLIENT001", t4, DeliveryStatus.COMPLETED));
        repository.save(notification("EVT005", "CLIENT001", t5, DeliveryStatus.COMPLETED));

        NotificationPage page1 = repository.search(query("CLIENT001", null, 2));
        assertEquals(List.of("EVT005", "EVT004"), ids(page1));

        NotificationPage page2 = repository.search(query("CLIENT001", page1.nextCursor(), 2));
        assertEquals(List.of("EVT003", "EVT002"), ids(page2));

        NotificationPage page3 = repository.search(query("CLIENT001", page2.nextCursor(), 2));
        assertEquals(List.of("EVT001"), ids(page3));
        assertNull(page3.nextCursor());
    }

    @Test
    void claimForReplayOnlyTransitionsFromFailed() {
        repository.save(notification("EVT001", "CLIENT001", T1, DeliveryStatus.FAILED));

        assertTrue(repository.claimForReplay(new EventId("EVT001"), new ClientId("CLIENT001")));
        assertEquals(
                DeliveryStatus.DELIVERING,
                repository.findForDelivery(new EventId("EVT001")).orElseThrow().deliveryStatus());
        assertFalse(repository.claimForReplay(new EventId("EVT001"), new ClientId("CLIENT001")));

        repository.save(notification("EVT002", "CLIENT001", T1, DeliveryStatus.FAILED));
        assertFalse(repository.claimForReplay(new EventId("EVT002"), new ClientId("CLIENT999")));

        repository.save(notification("EVT003", "CLIENT001", T1, DeliveryStatus.COMPLETED));
        assertFalse(repository.claimForReplay(new EventId("EVT003"), new ClientId("CLIENT001")));
    }

    @Test
    void claimForReplayIsAtomicUnderConcurrency() throws Exception {
        repository.save(notification("EVT001", "CLIENT001", T1, DeliveryStatus.FAILED));

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return repository.claimForReplay(new EventId("EVT001"), new ClientId("CLIENT001"));
            }));
        }
        start.countDown();

        long successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successes++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, successes);
    }

    private NotificationPage search(String client, Instant from, Instant to, Set<DeliveryStatus> statuses) {
        return repository.search(new NotificationQuery(new ClientId(client), from, to, statuses, null, 100));
    }

    private static NotificationQuery query(
            String client, com.cobre.eventnotifications.application.port.Cursor cursor, int pageSize) {
        return new NotificationQuery(new ClientId(client), null, null, Set.of(), cursor, pageSize);
    }

    private static List<String> ids(NotificationPage page) {
        return page.items().stream().map(n -> n.id().value()).toList();
    }

    private static Notification notification(String id, String client, Instant occurredAt, DeliveryStatus status) {
        List<DeliveryAttempt> attempts = status == DeliveryStatus.PENDING
                ? List.of()
                : List.of(new DeliveryAttempt(
                        1,
                        occurredAt,
                        URL,
                        status == DeliveryStatus.COMPLETED ? 200 : null,
                        status == DeliveryStatus.COMPLETED ? DeliveryResult.SUCCESS : DeliveryResult.FAILURE,
                        null));
        return new Notification(
                new EventId(id),
                new ClientId(client),
                new SubscriptionId("SUB001"),
                new EventType("credit_transfer"),
                "content",
                occurredAt,
                status,
                attempts);
    }
}
