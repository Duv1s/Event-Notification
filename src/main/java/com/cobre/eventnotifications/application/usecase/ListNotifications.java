package com.cobre.eventnotifications.application.usecase;

import com.cobre.eventnotifications.application.port.NotificationPage;
import com.cobre.eventnotifications.application.port.NotificationQuery;
import com.cobre.eventnotifications.application.port.NotificationRepository;
import java.util.Objects;

/** Lists the calling client's notifications using keyset pagination. */
public class ListNotifications {

    private final NotificationRepository notifications;

    public ListNotifications(NotificationRepository notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    public NotificationPage handle(NotificationQuery query) {
        return notifications.search(query);
    }
}
