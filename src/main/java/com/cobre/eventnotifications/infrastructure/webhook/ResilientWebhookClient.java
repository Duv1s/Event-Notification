package com.cobre.eventnotifications.infrastructure.webhook;

import com.cobre.eventnotifications.application.port.DeliveryOutcome;
import com.cobre.eventnotifications.application.port.WebhookClient;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.WebhookUrl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Real HTTPS webhook delivery using {@link RestClient}, with an SSRF guard, an HMAC signature, and
 * Resilience4j (retry as the outer decorator, a per-destination circuit breaker inside).
 *
 * <p>{@link #send} returns the FINAL outcome after the in-process retries. One {@code DeliveryAttempt}
 * is therefore recorded per delivery invocation: the in-process retries are collapsed. In production
 * each spaced retry (re-consumed by a worker) is a separate attempt.
 *
 * <p>Each invocation records a delivery counter and latency timer, tagged only with low-cardinality
 * dimensions ({@code outcome}, {@code event_type}) — never the client or subscription id.
 */
public class ResilientWebhookClient implements WebhookClient {

    private static final String SOURCE = "/platform/notifications";
    private static final String CONTENT_TYPE = "application/cloudevents+json";
    private static final int MAX_BODY_FRAGMENT = 64 * 1024;

    private final RestClient restClient;
    private final SsrfGuard ssrfGuard;
    private final Retry retry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResilientWebhookClient(
            RestClient restClient,
            SsrfGuard ssrfGuard,
            Retry retry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.ssrfGuard = Objects.requireNonNull(ssrfGuard, "ssrfGuard");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.circuitBreakerRegistry = Objects.requireNonNull(circuitBreakerRegistry, "circuitBreakerRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    /** Builds the retry policy: full-jitter exponential backoff, honouring Retry-After (capped). */
    public static RetryConfig webhookRetryConfig(
            int maxAttempts, long baseMillis, long maxBackoffMillis, long retryAfterCapMillis) {
        IntervalBiFunction<DeliveryOutcome> interval =
                (attempt, either) -> waitMillis(attempt, either, baseMillis, maxBackoffMillis, retryAfterCapMillis);
        return RetryConfig.<DeliveryOutcome>custom()
                .maxAttempts(maxAttempts)
                .retryExceptions(RetryableDeliveryException.class)
                .intervalBiFunction(interval)
                .build();
    }

    /** Per-destination circuit breaker policy; only retryable failures count toward opening it. */
    public static CircuitBreakerConfig webhookCircuitBreakerConfig(
            int slidingWindowSize, int minimumNumberOfCalls, float failureRateThreshold, Duration waitInOpenState) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitInOpenState)
                .recordExceptions(RetryableDeliveryException.class)
                .build();
    }

    private static long waitMillis(
            int attempt, Either<Throwable, DeliveryOutcome> either, long base, long maxBackoff, long retryAfterCap) {
        if (either != null
                && either.isLeft()
                && either.getLeft() instanceof RetryableDeliveryException failure
                && failure.retryAfter().isPresent()) {
            return Math.min(failure.retryAfter().get().toMillis(), retryAfterCap);
        }
        long exponential = Math.min((long) (base * Math.pow(2, Math.max(0, attempt - 1))), maxBackoff);
        return exponential <= 0 ? 0 : ThreadLocalRandom.current().nextLong(exponential + 1); // full jitter [0, cap]
    }

    @Override
    public DeliveryOutcome send(Notification notification, Subscription subscription) {
        long startNanos = System.nanoTime();
        DeliveryOutcome outcome = attemptDelivery(notification, subscription);
        recordMetrics(outcome, notification.eventType().value(), System.nanoTime() - startNanos);
        return outcome;
    }

    private DeliveryOutcome attemptDelivery(Notification notification, Subscription subscription) {
        try {
            ssrfGuard.validate(subscription.url());
        } catch (BlockedTargetException e) {
            return DeliveryOutcome.permanentFailure(null, "blocked by SSRF guard: " + e.getMessage());
        }

        byte[] body = serialize(notification);
        long timestamp = clock.instant().getEpochSecond();
        Map<String, String> headers = buildHeaders(notification, subscription, timestamp, body);
        WebhookUrl url = subscription.url();
        CircuitBreaker breaker =
                circuitBreakerRegistry.circuitBreaker(url.toUri().getHost());

        Supplier<DeliveryOutcome> attempt = () -> attemptOnce(url, body, headers);
        Supplier<DeliveryOutcome> decorated =
                Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, attempt));

        try {
            return decorated.get();
        } catch (RetryableDeliveryException e) {
            return DeliveryOutcome.retryableFailure(e.httpStatus(), "retries exhausted: " + e.getMessage());
        } catch (CallNotPermittedException e) {
            return DeliveryOutcome.retryableFailure(null, "circuit breaker open for destination");
        }
    }

    private void recordMetrics(DeliveryOutcome outcome, String eventType, long durationNanos) {
        String result = outcome.success() ? "success" : outcome.retryable() ? "retryable_failure" : "permanent_failure";
        meterRegistry
                .counter("webhook.deliveries", "outcome", result, "event_type", eventType)
                .increment();
        meterRegistry
                .timer("webhook.delivery.duration", "outcome", result, "event_type", eventType)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private DeliveryOutcome attemptOnce(WebhookUrl url, byte[] body, Map<String, String> headers) {
        try {
            return restClient
                    .post()
                    .uri(url.toUri())
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body)
                    .exchange((request, response) -> mapResponse(response));
        } catch (ResourceAccessException e) {
            if (hasCause(e, SSLException.class)) {
                return DeliveryOutcome.permanentFailure(null, "TLS error: " + e.getMessage());
            }
            throw new RetryableDeliveryException(null, "network error: " + e.getMessage(), null);
        }
    }

    private static DeliveryOutcome mapResponse(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (response.getStatusCode().is2xxSuccessful()) {
            return DeliveryOutcome.success(status);
        }
        String fragment = readFragment(response);
        String detail = "HTTP " + status + (fragment.isEmpty() ? "" : ": " + fragment);
        if (isRetryable(status)) {
            throw new RetryableDeliveryException(status, detail, parseRetryAfter(response.getHeaders()));
        }
        // 3xx are not followed, 4xx are client/config errors: non-retryable.
        return DeliveryOutcome.permanentFailure(status, detail);
    }

    private static boolean isRetryable(int status) {
        return status == 429 || (status >= 500 && status <= 599);
    }

    private static String readFragment(ClientHttpResponse response) {
        try (InputStream in = response.getBody()) {
            return new String(in.readNBytes(MAX_BODY_FRAGMENT), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static Duration parseRetryAfter(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null; // HTTP-date form is not handled in the demo
        }
    }

    private byte[] serialize(Notification notification) {
        CloudEventEnvelope envelope = new CloudEventEnvelope(
                "1.0",
                notification.id().value(),
                SOURCE,
                notification.eventType().value(),
                notification.occurredAt().toString(),
                notification.clientId().value(),
                "application/json",
                new CloudEventEnvelope.Data(notification.content()));
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize CloudEvent envelope", e);
        }
    }

    private Map<String, String> buildHeaders(
            Notification notification, Subscription subscription, long timestamp, byte[] body) {
        String signature = "t=" + timestamp + ",v1=" + hmacHex(subscription.hmacSecret(), timestamp, body);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("X-Signature", signature);
        headers.put("X-Event-Id", notification.id().value());
        headers.put("X-Delivery-Attempt", Integer.toString(notification.attemptCount() + 1));
        return headers;
    }

    private static String hmacHex(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
