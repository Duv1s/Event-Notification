package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.application.port.DeliveryNotificationStore;
import com.cobre.eventnotifications.application.port.SubscriptionRepository;
import com.cobre.eventnotifications.application.port.WebhookClient;
import com.cobre.eventnotifications.application.usecase.DeliverNotification;
import com.cobre.eventnotifications.infrastructure.webhook.ResilientWebhookClient;
import com.cobre.eventnotifications.infrastructure.webhook.SsrfGuard;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Wires the real webhook delivery: an Apache HttpClient 5 backed {@link RestClient} (full TLS
 * verification, no redirect following, connect/read timeouts), the SSRF guard, Resilience4j policies,
 * the {@link DeliverNotification} use case and a bounded async executor for {@code @Async} dispatch.
 */
@Configuration
@EnableAsync
public class WebhookConfiguration {

    @Bean
    public SsrfGuard ssrfGuard() {
        return new SsrfGuard();
    }

    @Bean
    public RestClient webhookRestClient() {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(2))
                .build();
        RequestConfig requestConfig =
                RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(5)).build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .build();
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    @Bean
    public CircuitBreakerRegistry webhookCircuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(
                ResilientWebhookClient.webhookCircuitBreakerConfig(20, 10, 50f, Duration.ofSeconds(30)));
    }

    @Bean
    public WebhookClient webhookClient(
            RestClient webhookRestClient,
            SsrfGuard ssrfGuard,
            CircuitBreakerRegistry webhookCircuitBreakerRegistry,
            Clock clock) {
        Retry retry = Retry.of("webhook", ResilientWebhookClient.webhookRetryConfig(4, 500, 5000, 60_000));
        return new ResilientWebhookClient(webhookRestClient, ssrfGuard, retry, webhookCircuitBreakerRegistry, clock);
    }

    @Bean
    public DeliverNotification deliverNotification(
            DeliveryNotificationStore store,
            SubscriptionRepository subscriptions,
            WebhookClient webhookClient,
            Clock clock) {
        return new DeliverNotification(store, subscriptions, webhookClient, clock);
    }

    @Bean(name = "webhookDeliveryExecutor")
    public Executor webhookDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("webhook-delivery-");
        executor.initialize();
        return executor;
    }
}
