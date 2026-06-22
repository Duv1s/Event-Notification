package com.cobre.eventnotifications.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Jackson-mapped shape of one subscription in {@code subscriptions.json}. */
record SubscriptionSeedDto(
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("event_type_filter") String eventTypeFilter,
        @JsonProperty("url") String url,
        @JsonProperty("hmac_secret") String hmacSecret,
        @JsonProperty("state") String state) {}
