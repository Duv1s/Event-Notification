package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.application.usecase.GetNotification;
import com.cobre.eventnotifications.application.usecase.ListNotifications;
import com.cobre.eventnotifications.application.usecase.ReplayNotification;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free use cases as Spring beans by injecting their ports.
 *
 * <p>{@code DeliverNotification} is wired in the webhook-adapter phase, once a {@code WebhookClient}
 * bean exists.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ListNotifications listNotifications(NotificationRepository notifications) {
        return new ListNotifications(notifications);
    }

    @Bean
    public GetNotification getNotification(NotificationRepository notifications) {
        return new GetNotification(notifications);
    }

    @Bean
    public ReplayNotification replayNotification(
            NotificationRepository notifications, SubscriptionRepository subscriptions, DeliveryDispatcher dispatcher) {
        return new ReplayNotification(notifications, subscriptions, dispatcher);
    }
}
