package com.cobre.eventnotifications.infrastructure.webhook;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobre.eventnotifications.application.port.DeliveryOutcome;
import com.cobre.eventnotifications.domain.ClientId;
import com.cobre.eventnotifications.domain.DeliveryStatus;
import com.cobre.eventnotifications.domain.EventId;
import com.cobre.eventnotifications.domain.EventType;
import com.cobre.eventnotifications.domain.EventTypeFilter;
import com.cobre.eventnotifications.domain.Notification;
import com.cobre.eventnotifications.domain.Subscription;
import com.cobre.eventnotifications.domain.SubscriptionId;
import com.cobre.eventnotifications.domain.SubscriptionState;
import com.cobre.eventnotifications.domain.WebhookUrl;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class ResilientWebhookClientTest {

    static {
        // WireMock's self-signed cert is for a generic host; skip hostname verification for the test client only.
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    }

    @RegisterExtension
    static final WireMockExtension WM = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
            .build();

    private static final String SECRET = "test-hmac-secret";
    private static final String PATH = "/webhook";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-03-15T10:00:00Z"), ZoneOffset.UTC);
    private static final long EXPECTED_TS = CLOCK.instant().getEpochSecond();
    private static final SsrfGuard PERMISSIVE_GUARD = new SsrfGuard() {
        @Override
        public void validate(WebhookUrl url) {
            // bypass for localhost WireMock; the real guard is covered by SsrfGuardTest
        }
    };

    private RestClient restClient;
    private String httpsBaseUrl;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        restClient = trustAllRestClient();
        httpsBaseUrl = WM.getRuntimeInfo().getHttpsBaseUrl();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void successReturnsCompletedOutcome() {
        WM.stubFor(post(PATH).willReturn(ok()));

        DeliveryOutcome outcome = client(4, lenientCircuitBreaker()).send(notification(), subscription());

        assertTrue(outcome.success());
        assertEquals(200, outcome.httpStatus());
        WM.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void recordsDeliveryMetricsWithLowCardinalityTagsOnly() {
        WM.stubFor(post(PATH).willReturn(ok()));

        client(1, lenientCircuitBreaker()).send(notification(), subscription());

        var counter = meterRegistry
                .find("webhook.deliveries")
                .tag("outcome", "success")
                .counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        assertEquals(1L, meterRegistry.find("webhook.delivery.duration").timer().count());
        assertTrue(counter.getId().getTags().stream()
                .noneMatch(
                        tag -> tag.getKey().equals("client_id") || tag.getKey().equals("subscription_id")));
    }

    @Test
    void serverErrorIsRetriedThenReturnsRetryableFailure() {
        WM.stubFor(post(PATH).willReturn(serverError()));

        DeliveryOutcome outcome = client(3, lenientCircuitBreaker()).send(notification(), subscription());

        assertFalse(outcome.success());
        assertTrue(outcome.retryable());
        WM.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void clientErrorFailsWithoutRetry() {
        WM.stubFor(post(PATH).willReturn(badRequest()));

        DeliveryOutcome outcome = client(3, lenientCircuitBreaker()).send(notification(), subscription());

        assertFalse(outcome.success());
        assertFalse(outcome.retryable());
        WM.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void timeoutIsRetryable() {
        WM.stubFor(post(PATH).willReturn(ok().withFixedDelay(3000))); // > 1s read timeout

        DeliveryOutcome outcome = client(2, lenientCircuitBreaker()).send(notification(), subscription());

        assertFalse(outcome.success());
        assertTrue(outcome.retryable());
    }

    @Test
    void retryAfterIsHonoured() {
        WM.stubFor(post(PATH)
                .inScenario("retry-after")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("recovered"));
        WM.stubFor(post(PATH)
                .inScenario("retry-after")
                .whenScenarioStateIs("recovered")
                .willReturn(ok()));

        long start = System.nanoTime();
        DeliveryOutcome outcome = client(3, lenientCircuitBreaker()).send(notification(), subscription());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(outcome.success());
        assertTrue(
                elapsedMillis >= 800, "should have waited ~Retry-After before retrying, was " + elapsedMillis + "ms");
        WM.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void redirectIsNotFollowed() {
        WM.stubFor(
                post(PATH).willReturn(aResponse().withStatus(302).withHeader("Location", httpsBaseUrl + "/elsewhere")));

        DeliveryOutcome outcome = client(3, lenientCircuitBreaker()).send(notification(), subscription());

        assertFalse(outcome.success());
        assertFalse(outcome.retryable());
        WM.verify(1, postRequestedFor(urlEqualTo(PATH))); // not followed
    }

    @Test
    void signsRequestWithHmacOverTimestampAndBody() throws Exception {
        WM.stubFor(post(PATH).willReturn(ok()));
        Notification notification = notification();

        client(1, lenientCircuitBreaker()).send(notification, subscription());

        LoggedRequest request = WM.findAll(postRequestedFor(urlEqualTo(PATH))).get(0);
        String signature = request.getHeader("X-Signature");
        assertTrue(signature.matches("t=\\d+,v1=[0-9a-f]{64}"), "unexpected signature format: " + signature);

        long timestamp = Long.parseLong(signature.split(",")[0].substring(2));
        String v1 = signature.split(",")[1].substring(3);
        assertEquals(EXPECTED_TS, timestamp);
        assertEquals(hmacHex(SECRET, timestamp, request.getBody()), v1);
        assertEquals(notification.id().value(), request.getHeader("X-Event-Id"));
    }

    @Test
    void circuitBreakerOpensForDestinationAfterThreshold() {
        WM.stubFor(post(PATH).willReturn(serverError()));
        CircuitBreakerRegistry registry = aggressiveCircuitBreaker();
        ResilientWebhookClient client = client(1, registry); // one attempt per send

        client.send(notification(), subscription()); // failure 1
        client.send(notification(), subscription()); // failure 2 -> opens
        DeliveryOutcome blocked = client.send(notification(), subscription());

        assertTrue(blocked.retryable());
        assertTrue(blocked.error().contains("circuit breaker open"), "was: " + blocked.error());
        WM.verify(2, postRequestedFor(urlEqualTo(PATH))); // third call short-circuited
    }

    // --- helpers ---

    private ResilientWebhookClient client(int maxAttempts, CircuitBreakerRegistry circuitBreakers) {
        Retry retry = Retry.of("test", ResilientWebhookClient.webhookRetryConfig(maxAttempts, 10, 50, 2000));
        return new ResilientWebhookClient(restClient, PERMISSIVE_GUARD, retry, circuitBreakers, CLOCK, meterRegistry);
    }

    private static CircuitBreakerRegistry lenientCircuitBreaker() {
        return CircuitBreakerRegistry.of(
                ResilientWebhookClient.webhookCircuitBreakerConfig(100, 100, 100f, Duration.ofSeconds(1)));
    }

    private static CircuitBreakerRegistry aggressiveCircuitBreaker() {
        return CircuitBreakerRegistry.of(
                ResilientWebhookClient.webhookCircuitBreakerConfig(2, 2, 50f, Duration.ofSeconds(10)));
    }

    private Subscription subscription() {
        return new Subscription(
                new SubscriptionId("SUB002"),
                new ClientId("CLIENT002"),
                EventTypeFilter.all(),
                new WebhookUrl(httpsBaseUrl + PATH),
                SECRET,
                SubscriptionState.ACTIVE);
    }

    private static Notification notification() {
        return new Notification(
                new EventId("EVT003"),
                new ClientId("CLIENT002"),
                new SubscriptionId("SUB002"),
                new EventType("credit_transfer"),
                "Bank transfer received",
                Instant.parse("2024-03-15T11:20:18Z"),
                DeliveryStatus.PENDING,
                List.of());
    }

    private static String hmacHex(String secret, long timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(body);
        return HexFormat.of().formatHex(mac.doFinal());
    }

    private static RestClient trustAllRestClient() {
        try {
            TrustManager[] trustAll = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            HttpClient httpClient = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(1))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(1));
            return RestClient.builder().requestFactory(factory).build();
        } catch (Exception e) {
            throw new IllegalStateException("failed to build trust-all RestClient", e);
        }
    }
}
