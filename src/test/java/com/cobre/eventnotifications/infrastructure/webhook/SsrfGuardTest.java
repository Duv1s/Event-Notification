package com.cobre.eventnotifications.infrastructure.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cobre.eventnotifications.domain.WebhookUrl;
import org.junit.jupiter.api.Test;

class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    private void assertBlocked(String url) {
        assertThrows(BlockedTargetException.class, () -> guard.validate(new WebhookUrl(url)));
    }

    @Test
    void httpIsRejectedAtTheValueObjectBoundary() {
        // The WebhookUrl value object enforces https before the guard ever runs.
        assertThrows(IllegalArgumentException.class, () -> new WebhookUrl("http://example.com"));
    }

    @Test
    void blocksNonHttpsPort() {
        assertBlocked("https://example.com:8080");
    }

    @Test
    void blocksLoopbackV4() {
        assertBlocked("https://127.0.0.1");
    }

    @Test
    void blocksLoopbackV6() {
        assertBlocked("https://[::1]");
    }

    @Test
    void blocksPrivateRanges() {
        assertBlocked("https://10.0.0.1");
        assertBlocked("https://192.168.0.1");
        assertBlocked("https://172.16.0.1");
    }

    @Test
    void blocksLinkLocalMetadataAddress() {
        assertBlocked("https://169.254.169.254");
    }

    @Test
    void blocksIpv4MappedPrivateAddress() {
        assertBlocked("https://[::ffff:10.0.0.1]");
    }

    @Test
    void allowsPublicHttpsTargetOn443() {
        // Public IP literal on the default https port: scheme/port ok and not a blocked range.
        assertDoesNotThrow(() -> guard.validate(new WebhookUrl("https://93.184.216.34")));
    }
}
