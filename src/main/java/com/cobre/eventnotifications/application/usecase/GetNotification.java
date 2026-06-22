package com.cobre.eventnotifications.application.usecase;

import com.cobre.eventnotifications.application.exception.NotificationNotFoundException;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.Notification;
import java.util.Objects;

/** Returns a single notification owned by the calling client, or fails with 404. */
public class GetNotification {

    private final NotificationRepository notifications;

    public GetNotification(NotificationRepository notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    public Notification handle(EventId id, ClientId clientId) {
        return notifications.findByIdAndClientId(id, clientId).orElseThrow(() -> new NotificationNotFoundException(id));
    }
}
