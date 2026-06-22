package com.cobre.eventnotifications.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * OAuth2 resource server: every {@code /v1/**} request needs a valid JWT; actuator health and the
 * OpenAPI UI are public. The token is verified against a JWKS (a local test key by default, the IdP's
 * {@code jwk-set-uri} in production) and validated for signature, {@code iss}, {@code aud}, {@code
 * exp}/{@code nbf} and a required {@code client_id} claim. Method security enforces the scopes.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.security.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${app.security.jwt.jwks:classpath:jwt/demo-jwks.json}") Resource jwks,
            @Value("${app.security.jwt.issuer:demo-issuer}") String issuer,
            @Value("${app.security.jwt.audience:event-notifications}") String audience)
            throws Exception {
        NimbusJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withPublicKey(firstRsaPublicKey(jwks)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer),
                requireAudience(audience),
                requireClientId()));
        return decoder;
    }

    private static RSAPublicKey firstRsaPublicKey(Resource jwks) throws Exception {
        JWKSet jwkSet = JWKSet.parse(jwks.getContentAsString(StandardCharsets.UTF_8));
        return ((RSAKey) jwkSet.getKeys().get(0)).toRSAPublicKey();
    }

    private static OAuth2TokenValidator<Jwt> requireAudience(String audience) {
        OAuth2Error error = new OAuth2Error("invalid_token", "the required audience is missing", null);
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }

    private static OAuth2TokenValidator<Jwt> requireClientId() {
        OAuth2Error error = new OAuth2Error("invalid_token", "the client_id claim is required", null);
        return jwt -> StringUtils.hasText(jwt.getClaimAsString("client_id"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(error);
    }
}
