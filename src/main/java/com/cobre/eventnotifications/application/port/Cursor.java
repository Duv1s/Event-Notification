package com.cobre.eventnotifications.application.port;

import com.cobre.eventnotifications.domain.EventId;
import java.time.Instant;
import java.util.Objects;

/**
 * A decoded keyset cursor pointing at the {@code (occurredAt, id)} of the last item of a page.
 * Base64 encoding/decoding is a web-layer concern; here the cursor is already decoded.
 */
public record Cursor(Instant occurredAt, EventId id) {

    public Cursor {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(id, "id");
    }
}
