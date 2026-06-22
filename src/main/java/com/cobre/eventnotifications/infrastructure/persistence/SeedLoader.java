package com.cobre.eventnotifications.infrastructure.persistence;

import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryAttempt;
import com.cobre.eventnotifications.domain.DeliveryResult;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.EventType;
import com.cobre.eventnotifications.domain.EventTypeFilter;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.SubscriptionId;
import com.cobre.eventnotifications.domain.SubscriptionState;
import com.cobre.eventnotifications.domain.WebhookUrl;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the seed data into the in-memory repositories at startup.
 *
 * <p>Documented seed assumptions: the source JSON has no event creation time, so {@code occurred_at}
 * is seeded from {@code delivery_date}; it has no subscription link, so each notification is assigned
 * its client's active subscription; and each notification gets one initial {@link DeliveryAttempt}
 * reflecting the seeded {@code delivery_status}.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private final ObjectMapper objectMapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final InMemorySubscriptionRepository subscriptions;
    private final NotificationRepository notifications;
    private final Resource subscriptionsResource;
    private final Resource notificationsResource;

    public SeedLoader(
            InMemorySubscriptionRepository subscriptions,
            NotificationRepository notifications,
            @Value("${app.seed.subscriptions}") Resource subscriptionsResource,
            @Value("${app.seed.notifications}") Resource notificationsResource) {
        this.subscriptions = subscriptions;
        this.notifications = notifications;
        this.subscriptionsResource = subscriptionsResource;
        this.notificationsResource = notificationsResource;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Map<ClientId, Subscription> activeByClient = loadSubscriptions();
        loadNotifications(activeByClient);
    }

    private Map<ClientId, Subscription> loadSubscriptions() throws IOException {
        SubscriptionSeedFile file = read(subscriptionsResource, SubscriptionSeedFile.class);
        Map<ClientId, Subscription> activeByClient = new HashMap<>();
        for (SubscriptionSeedDto dto : file.subscriptions()) {
            Subscription subscription = toSubscription(dto);
            subscriptions.add(subscription);
            if (subscription.isActive()) {
                activeByClient.putIfAbsent(subscription.clientId(), subscription);
            }
        }
        return activeByClient;
    }

    private void loadNotifications(Map<ClientId, Subscription> activeByClient) throws IOException {
        NotificationSeedFile file = read(notificationsResource, NotificationSeedFile.class);
        for (NotificationSeedDto dto : file.events()) {
            ClientId clientId = new ClientId(dto.clientId());
            Subscription subscription = activeByClient.get(clientId);
            if (subscription == null) {
                throw new IllegalStateException("No active subscription seeded for client " + clientId);
            }
            notifications.save(toNotification(dto, subscription));
        }
    }

    private <T> T read(Resource resource, Class<T> type) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    private static Subscription toSubscription(SubscriptionSeedDto dto) {
        return new Subscription(
                new SubscriptionId(dto.subscriptionId()),
                new ClientId(dto.clientId()),
                new EventTypeFilter(List.of(dto.eventTypeFilter())),
                new WebhookUrl(dto.url()),
                dto.hmacSecret(),
                SubscriptionState.valueOf(dto.state()));
    }

    private static Notification toNotification(NotificationSeedDto dto, Subscription subscription) {
        Instant occurredAt = Instant.parse(dto.deliveryDate());
        DeliveryStatus status =
                switch (dto.deliveryStatus()) {
                    case "completed" -> DeliveryStatus.COMPLETED;
                    case "failed" -> DeliveryStatus.FAILED;
                    default -> throw new IllegalStateException("Unknown delivery_status: " + dto.deliveryStatus());
                };
        boolean success = status == DeliveryStatus.COMPLETED;
        DeliveryAttempt initialAttempt = new DeliveryAttempt(
                1,
                occurredAt,
                subscription.url(),
                success ? 200 : null,
                success ? DeliveryResult.SUCCESS : DeliveryResult.FAILURE,
                success ? null : "seeded from dataset as failed");
        return new Notification(
                new EventId(dto.eventId()),
                new ClientId(dto.clientId()),
                subscription.subscriptionId(),
                new EventType(dto.eventType()),
                dto.content(),
                occurredAt,
                status,
                List.of(initialAttempt));
    }
}
