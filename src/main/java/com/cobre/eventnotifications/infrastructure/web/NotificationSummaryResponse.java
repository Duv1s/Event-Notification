package com.cobre.eventnotifications.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** List item: a notification summary without the attempt history. */
public record NotificationSummaryResponse(
        @JsonProperty("notification_event_id") String notificationEventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("last_attempt_at") String lastAttemptAt) {}
