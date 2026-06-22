package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.domain.ClientId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * TEMPORARY tenant resolution for development: reads the {@code X-Client-Id} header and falls back to
 * a dev default. Phase 7 replaces this with the {@code client_id} claim of the validated JWT.
 */
@Component
public class HeaderClientIdResolver implements ClientIdResolver {

    static final String CLIENT_ID_HEADER = "X-Client-Id";
    private static final String DEV_DEFAULT_CLIENT_ID = "CLIENT001";

    @Override
    public ClientId resolve(HttpServletRequest request) {
        String value = request.getHeader(CLIENT_ID_HEADER);
        if (value == null || value.isBlank()) {
            value = DEV_DEFAULT_CLIENT_ID;
        }
        return new ClientId(value);
    }
}
