package com.cobre.eventnotifications.infrastructure.web;

import com.cobre.eventnotifications.domain.ClientId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the tenant from the {@code client_id} claim of the authenticated JWT. There is no dev
 * fallback: the resource server already rejects tokens without {@code client_id} (401), so an
 * authenticated request always carries one.
 */
@Component
public class JwtClientIdResolver implements ClientIdResolver {

    @Override
    public ClientId resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String clientId = jwt.getToken().getClaimAsString("client_id");
            if (StringUtils.hasText(clientId)) {
                return new ClientId(clientId);
            }
        }
        throw new IllegalStateException("no authenticated client_id in the security context");
    }
}
