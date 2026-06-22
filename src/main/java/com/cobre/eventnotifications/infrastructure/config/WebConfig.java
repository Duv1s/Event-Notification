package com.cobre.eventnotifications.infrastructure.config;

import com.cobre.eventnotifications.infrastructure.web.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the per-client rate-limiting interceptor on the {@code /v1} API. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final long readPerMinute;
    private final long replayPerMinute;

    public WebConfig(
            @Value("${app.ratelimit.read-per-minute:100}") long readPerMinute,
            @Value("${app.ratelimit.replay-per-minute:10}") long replayPerMinute) {
        this.readPerMinute = readPerMinute;
        this.replayPerMinute = replayPerMinute;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(readPerMinute, replayPerMinute))
                .addPathPatterns("/v1/**");
    }
}
