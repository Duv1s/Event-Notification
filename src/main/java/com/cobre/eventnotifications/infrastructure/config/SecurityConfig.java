package com.cobre.eventnotifications.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY permissive security so the API, Swagger UI and actuator are reachable in development
 * (starter-security denies everything by default). Phase 7 replaces this with an OAuth2 resource
 * server validating JWTs and enforcing the {@code notifications:read} / {@code notifications:replay}
 * scopes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
