package com.cobre.eventnotifications;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cobre.eventnotifications.application.port.WebhookClient;
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
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookClient webhookClient;

    @Autowired
    private CircuitBreakerRegistry webhookCircuitBreakerRegistry;

    @Test
    void healthEndpointResponds() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void prometheusExposesDeliveryAndCircuitBreakerMetrics() throws Exception {
        // A delivery (blocked by the SSRF guard -> permanent_failure) registers the delivery metric.
        webhookClient.send(notification(), loopbackSubscription());
        // Materialise a circuit breaker so its bound meters are published.
        webhookCircuitBreakerRegistry.circuitBreaker("metrics-probe");

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("webhook_deliveries")))
                .andExpect(content().string(containsString("resilience4j_circuitbreaker")));
    }

    private static Notification notification() {
        return new Notification(
                new EventId("EVT001"),
                new ClientId("CLIENT001"),
                new SubscriptionId("SUB001"),
                new EventType("credit_transfer"),
                "content",
                Instant.parse("2024-03-15T09:30:22Z"),
                DeliveryStatus.PENDING,
                List.of());
    }

    private static Subscription loopbackSubscription() {
        return new Subscription(
                new SubscriptionId("SUB001"),
                new ClientId("CLIENT001"),
                EventTypeFilter.all(),
                new WebhookUrl("https://127.0.0.1"),
                "demo-secret",
                SubscriptionState.ACTIVE);
    }
}
