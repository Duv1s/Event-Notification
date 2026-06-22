package com.cobre.eventnotifications.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Root of {@code notification_events.json}. */
record NotificationSeedFile(@JsonProperty("events") List<NotificationSeedDto> events) {}
