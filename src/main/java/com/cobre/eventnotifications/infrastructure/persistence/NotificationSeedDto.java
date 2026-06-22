package com.cobre.eventnotifications.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Jackson-mapped shape of one event in {@code notification_events.json}. */
record NotificationSeedDto(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("content") String content,
        @JsonProperty("delivery_date") String deliveryDate,
        @JsonProperty("delivery_status") String deliveryStatus,
        @JsonProperty("client_id") String clientId) {}
