package com.cobre.eventnotifications.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Detail: a notification summary plus its ordered delivery-attempt history. */
public record NotificationDetailResponse(
        @JsonProperty("notification_event_id") String notificationEventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("occurred_at") String occurredAt,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("last_attempt_at") String lastAttemptAt,
        @JsonProperty("attempts") List<DeliveryAttemptResponse> attempts) {}
