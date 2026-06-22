package com.cobre.eventnotifications.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** A page of notification summaries plus the opaque cursor for the next page ({@code null} if none). */
public record NotificationPageResponse(
        @JsonProperty("items") List<NotificationSummaryResponse> items,
        @JsonProperty("next_cursor") String nextCursor) {}
