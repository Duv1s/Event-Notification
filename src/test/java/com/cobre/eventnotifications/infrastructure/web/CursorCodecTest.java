package com.cobre.eventnotifications.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cobre.eventnotifications.application.port.Cursor;
import com.cobre.eventnotifications.domain.EventId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    @Test
    void roundTripsCursor() {
        Cursor cursor = new Cursor(Instant.parse("2024-03-15T09:30:22Z"), new EventId("EVT001"));
        assertEquals(cursor, CursorCodec.decode(CursorCodec.encode(cursor)));
    }

    @Test
    void rejectsMalformedToken() {
        assertThrows(BadRequestException.class, () -> CursorCodec.decode("###not-base64###"));
    }

    @Test
    void rejectsTokenWithoutSeparator() {
        String token =
                Base64.getUrlEncoder().withoutPadding().encodeToString("no-separator".getBytes(StandardCharsets.UTF_8));
        assertThrows(BadRequestException.class, () -> CursorCodec.decode(token));
    }
}
