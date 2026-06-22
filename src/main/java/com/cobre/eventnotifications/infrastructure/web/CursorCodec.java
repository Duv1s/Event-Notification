package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.application.port.Cursor;
import com.cobre.eventnotifications.domain.EventId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes/decodes the keyset {@link Cursor} as an opaque, URL-safe base64 token. This base64 concern
 * is the web layer's; the application works with the decoded {@code (occurredAt, id)}.
 */
final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {}

    static String encode(Cursor cursor) {
        String raw = cursor.occurredAt() + SEPARATOR + cursor.id().value();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator <= 0) {
                throw new IllegalArgumentException("cursor is missing the separator");
            }
            Instant occurredAt = Instant.parse(raw.substring(0, separator));
            EventId id = new EventId(raw.substring(separator + 1));
            return new Cursor(occurredAt, id);
        } catch (RuntimeException e) {
            throw new BadRequestException("cursor is not a valid pagination token");
        }
    }
}
