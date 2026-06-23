package com.cobre.eventnotifications.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a per-request {@code trace_id} (an incoming {@code X-Request-Id} or a generated UUID) and the
 * authenticated {@code client_id} into the MDC so request logs are correlatable, and clears them
 * afterwards. Runs after Spring Security (default lowest precedence) so the tenant is available. No
 * PII is logged here. The delivery thread adds {@code event_id} separately.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "trace_id";
    private static final String CLIENT_ID = "client_id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        String traceId =
                StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        String clientId = currentClientId();
        if (clientId != null) {
            MDC.put(CLIENT_ID, clientId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(CLIENT_ID);
        }
    }

    private static String currentClientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString("client_id");
        }
        return null;
    }
}
