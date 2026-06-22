package com.cobre.eventnotifications.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Root of {@code subscriptions.json}. */
record SubscriptionSeedFile(@JsonProperty("subscriptions") List<SubscriptionSeedDto> subscriptions) {}
