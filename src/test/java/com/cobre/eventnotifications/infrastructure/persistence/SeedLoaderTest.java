package com.cobre.eventnotifications.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.SubscriptionId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SeedLoaderTest {

    private final InMemorySubscriptionRepository subscriptions = new InMemorySubscriptionRepository();
    private final InMemoryNotificationRepository notifications = new InMemoryNotificationRepository();

    @BeforeEach
    void loadSeed() throws Exception {
        new SeedLoader(
                        subscriptions,
                        notifications,
                        new ClassPathResource("subscriptions.json"),
                        new ClassPathResource("notification_events.json"))
                .run(null);
    }

    @Test
    void loadsCompletedNotificationWithSeededFields() {
        Notification evt001 =
                notifications.findForDelivery(new EventId("EVT001")).orElseThrow();
        assertEquals(new SubscriptionId("SUB001"), evt001.subscriptionId());
        assertEquals(Instant.parse("2024-03-15T09:30:22Z"), evt001.occurredAt());
        assertEquals(DeliveryStatus.COMPLETED, evt001.deliveryStatus());
        assertEquals(1, evt001.attemptCount());
    }

    @Test
    void assignsSubscriptionByClientAndKeepsFailedStatus() {
        Notification evt003 =
                notifications.findForDelivery(new EventId("EVT003")).orElseThrow();
        assertEquals(new SubscriptionId("SUB002"), evt003.subscriptionId());
        assertEquals(DeliveryStatus.FAILED, evt003.deliveryStatus());
    }

    @Test
    void loadsAllTenNotifications() {
        long total = count("CLIENT001") + count("CLIENT002") + count("CLIENT003");
        assertEquals(10, total);
    }

    private long count(String client) {
        return notifications
                .search(new NotificationQuery(new ClientId(client), null, null, Set.of(), null, 100))
                .items()
                .size();
    }
}
