package com.cobre.eventnotifications.infrastructure.web;

import java.time.Duration;
import java.time.Instant;

/**
 * Prints a demo JWT signed with the test-only key, so a reviewer can call the live API. Run it with
 * {@code ./gradlew -q printDemoToken} (defaults to CLIENT001) or {@code ./gradlew -q printDemoToken
 * --args=CLIENT002}. The signing key stays in {@code src/test/resources}; nothing secret ships in the
 * runtime jar.
 */
public final class DemoTokenPrinter {

    private DemoTokenPrinter() {}

    public static void main(String[] args) {
        String clientId = args.length > 0 && !args[0].isBlank() ? args[0] : "CLIENT001";
        String token = JwtTestTokens.token(
                clientId,
                JwtTestTokens.ISSUER,
                JwtTestTokens.AUDIENCE,
                Instant.now().plus(Duration.ofDays(365)),
                "notifications:read",
                "notifications:replay");
        System.out.println(token);
    }
}
