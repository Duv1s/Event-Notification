package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.Notification;
import java.util.List;

/**
 * A page of notifications plus the cursor to fetch the next page.
 *
 * @param items the page items (never null)
 * @param nextCursor the cursor for the next page, or {@code null} when there are no more pages
 */
public record NotificationPage(List<Notification> items, Cursor nextCursor) {

    public NotificationPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
