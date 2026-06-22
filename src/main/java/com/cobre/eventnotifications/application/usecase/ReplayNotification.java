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
 *   <li>it must be replayable, i.e. FAILED (409 otherwise);
 *   <li>the current subscription must still be ACTIVE and still cover the event type (409 otherwise);
 *   <li>atomically claim FAILED -&gt; DELIVERING and, only if the claim wins, trigger delivery.
 * </ol>
 *
 * <p>The atomic claim is the LAST step, so if any earlier check fails nothing is mutated and the
 * notification stays FAILED. A lost claim (concurrent replay won, or the status changed) is reported
 * as a 409.
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

        Subscription subscription = subscriptions
                .findById(notification.subscriptionId())
                .orElseThrow(() -> new SubscriptionNotEligibleException(id, notification.subscriptionId()));
        if (!subscription.isActive() || !subscription.covers(notification.eventType())) {
            throw new SubscriptionNotEligibleException(id, notification.subscriptionId());
        }

        if (!notifications.claimForReplay(id, clientId)) {
            throw new ReplayNotAllowedException(id, notification.deliveryStatus());
        }
        dispatcher.dispatch(id);
    }
}
