package com.cobre.eventnotifications.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One delivery attempt in the detail response. */
public record DeliveryAttemptResponse(
        @JsonProperty("attempt_number") int attemptNumber,
        @JsonProperty("attempted_at") String attemptedAt,
        @JsonProperty("url_used") String urlUsed,
        @JsonProperty("http_status") Integer httpStatus,
        @JsonProperty("result") String result,
        @JsonProperty("error") String error) {}
