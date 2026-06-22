package com.cobre.eventnotifications.infrastructure.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

/** CloudEvents 1.0 structured envelope sent as the webhook body. */
record CloudEventEnvelope(
        @JsonProperty("specversion") String specversion,
        @JsonProperty("id") String id,
        @JsonProperty("source") String source,
        @JsonProperty("type") String type,
        @JsonProperty("time") String time,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("datacontenttype") String datacontenttype,
        @JsonProperty("data") Data data) {

    record Data(@JsonProperty("content") String content) {}
}
