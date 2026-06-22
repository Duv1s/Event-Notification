package com.cobre.eventnotifications.domain;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A webhook destination URL.
 *
 * <p>Validates only that the value is a syntactically valid URI with an {@code https} scheme and a
 * host. The deep SSRF guard (private/loopback/link-local/metadata IP ranges, DNS resolution and
 * pinning) is intentionally NOT here: it belongs to the outbound webhook adapter, because it depends
 * on runtime DNS resolution and must run on every delivery.
 */
public record WebhookUrl(String value) {

    public WebhookUrl {
        validateHttpsUrl(value);
    }

    public URI toUri() {
        return URI.create(value);
    }

    private static void validateHttpsUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("webhook url must not be blank");
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("webhook url is not a valid URI: " + value, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("webhook url must use the https scheme: " + value);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("webhook url must have a host: " + value);
        }
    }
}
