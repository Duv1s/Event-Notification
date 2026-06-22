package com.cobre.eventnotifications.infrastructure.webhook;

import com.cobre.eventnotifications.domain.WebhookUrl;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Outbound SSRF guard, run before every delivery (the webhook URL is client-controlled).
 *
 * <p>Allows only {@code https} on port 443, then resolves DNS and rejects the target if ANY resolved
 * address is loopback / any-local / link-local (incl. the {@code 169.254.169.254} metadata address) /
 * site-local (private) / multicast / IPv6 unique-local. IPv4-mapped IPv6 addresses are normalised by
 * the JVM to IPv4 and caught by the same rules.
 *
 * <p>Residual TOCTOU: the IPs are validated just before the call rather than pinned into the
 * connection, so a racing DNS rebind between validation and connect is theoretically possible. This
 * is a conscious trade-off for the in-memory demo; production would pin the validated IP.
 */
public class SsrfGuard {

    private static final int HTTPS_PORT = 443;

    public void validate(WebhookUrl url) {
        URI uri = url.toUri();
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BlockedTargetException("only https is allowed, got: " + uri.getScheme());
        }
        int port = uri.getPort() == -1 ? HTTPS_PORT : uri.getPort();
        if (port != HTTPS_PORT) {
            throw new BlockedTargetException("only port 443 is allowed, got: " + port);
        }
        String host = stripBrackets(uri.getHost());
        if (host == null || host.isBlank()) {
            throw new BlockedTargetException("missing host");
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BlockedTargetException("host does not resolve: " + host);
        }
        for (InetAddress address : resolved) {
            if (isBlocked(address)) {
                throw new BlockedTargetException("target resolves to a blocked address: " + address.getHostAddress());
            }
        }
    }

    private static boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress() // 127/8, ::1
                || address.isAnyLocalAddress() // 0.0.0.0, ::
                || address.isLinkLocalAddress() // 169.254/16 (incl. 169.254.169.254), fe80::/10
                || address.isSiteLocalAddress() // 10/8, 172.16/12, 192.168/16
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address ipv6) {
            // fc00::/7 unique local addresses (not covered by isSiteLocalAddress for IPv6).
            return (ipv6.getAddress()[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private static String stripBrackets(String host) {
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
