package com.cobre.eventnotifications.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of the 202 Accepted response returned by replay; the notification is now in progress. */
public record ReplayResponse(
        @JsonProperty("notification_event_id") String notificationEventId,
        @JsonProperty("delivery_status") String deliveryStatus) {}
