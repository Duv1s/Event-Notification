package com.cobre.eventnotifications.application.usecase;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.exception.ReplayNotAllowedException;
import com.cobre.eventnotifications.application.exception.SubscriptionNotEligibleException;
import com.cobre.eventnotifications.application.port.DeliveryDispatcher;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import java.util.Objects;

/**
 * Re-delivers a definitively failed notification, following the order of the system design (6.3):
 *
 * <ol>
 *   <li>load tenant-scoped (404 if missing);
 *   <li>claim it with a FAILED -&gt; DELIVERING transition (409 if not FAILED);
 *   <li>re-resolve the current subscription, which must still be ACTIVE and still cover the event type
 *       (409 otherwise);
 *   <li>persist the claim and trigger asynchronous delivery.
 * </ol>
 *
 * <p>The true atomicity of the claim against concurrent double-replay is provided by the repository
 * adapter (e.g. {@code ConcurrentHashMap.compute}); this use case expresses the guarded transition.
 * If the subscription check fails after the in-memory claim, nothing is persisted, so the stored
 * notification stays FAILED.
 */
public class ReplayNotification {

    private final NotificationRepository notifications;
    private final SubscriptionRepository subscriptions;
    private final DeliveryDispatcher dispatcher;

    public ReplayNotification(
            NotificationRepository notifications, SubscriptionRepository subscriptions, DeliveryDispatcher dispatcher) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public void handle(EventId id, ClientId clientId) {
        Notification notification = notifications
                .findByIdAndClientId(id, clientId)
                .orElseThrow(() -> new NotificationNotFoundException(id));

        if (!notification.canBeReplayed()) {
            throw new ReplayNotAllowedException(id, notification.deliveryStatus());
        }
        notification.replay();

        Subscription subscription = subscriptions
                .findById(notification.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotEligibleException(id, notification.subscriptionId()));
        if (!subscription.isActive() || !subscription.covers(notification.eventType())) {
            throw new SubscriptionNotEligibleException(id, notification.subscriptionId());
        }

        notifications.save(notification);
        dispatcher.dispatch(id);
    }
}
