package com.cobre.eventnotifications.infrastructure.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * In-app rate-limiting backstop (the gateway enforces it too in production). Per {@code client_id},
 * two tiers by operation: reads vs. the more expensive replay. Over-limit returns 429 with
 * {@code Retry-After} and {@code RateLimit-*} headers.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final long readPerMinute;
    private final long replayPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(long readPerMinute, long replayPerMinute) {
        this.readPerMinute = readPerMinute;
        this.replayPerMinute = replayPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String clientId = currentClientId();
        if (clientId == null) {
            return true; // unauthenticated requests are handled by Spring Security
        }
        boolean replay = isReplay(request);
        long limit = replay ? replayPerMinute : readPerMinute;
        Bucket bucket = buckets.computeIfAbsent((replay ? "replay:" : "read:") + clientId, key -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long resetSeconds =
                Math.max(0, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setHeader("RateLimit-Limit", Long.toString(limit));
        response.setHeader("RateLimit-Remaining", Long.toString(Math.max(0, probe.getRemainingTokens())));
        response.setHeader("RateLimit-Reset", Long.toString(resetSeconds));
        if (probe.isConsumed()) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(Math.max(1, resetSeconds)));
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                        {"type":"https://api.cobre.example/problems/rate-limited","title":"Too Many Requests",\
                        "status":429,"detail":"rate limit exceeded for this client","code":"RATE_LIMITED"}""");
        return false;
    }

    private boolean isReplay(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().endsWith("/replay");
    }

    private static Bucket newBucket(long perMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(perMinute)
                .refillGreedy(perMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String currentClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString("client_id");
        }
        return null;
    }
}
