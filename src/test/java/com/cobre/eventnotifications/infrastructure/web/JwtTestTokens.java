package com.cobre.eventnotifications.infrastructure.web;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.core.io.ClassPathResource;

/** Signs test JWTs with the demo private JWK that matches the resource server's local JWKS. */
final class JwtTestTokens {

    static final String ISSUER = "demo-issuer";
    static final String AUDIENCE = "event-notifications";

    private static final RSAKey SIGNING_KEY = loadSigningKey();

    private JwtTestTokens() {}

    static String valid(String clientId, String... scopes) {
        return token(clientId, ISSUER, AUDIENCE, Instant.now().plusSeconds(300), scopes);
    }

    static String expired(String clientId, String... scopes) {
        return token(clientId, ISSUER, AUDIENCE, Instant.now().minusSeconds(60), scopes);
    }

    static String wrongAudience(String clientId, String... scopes) {
        return token(clientId, ISSUER, "some-other-api", Instant.now().plusSeconds(300), scopes);
    }

    static String withoutClientId(String... scopes) {
        return token(null, ISSUER, AUDIENCE, Instant.now().plusSeconds(300), scopes);
    }

    static String token(String clientId, String issuer, String audience, Instant expiry, String... scopes) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject("test-app")
                    .issueTime(Date.from(Instant.now().minusSeconds(5)))
                    .expirationTime(Date.from(expiry));
            if (clientId != null) {
                claims.claim("client_id", clientId);
            }
            if (scopes.length > 0) {
                claims.claim("scope", String.join(" ", scopes));
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(SIGNING_KEY.getKeyID())
                            .build(),
                    claims.build());
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign test JWT", e);
        }
    }

    private static RSAKey loadSigningKey() {
        try {
            String json = new ClassPathResource("jwt/demo-signing-jwk.json").getContentAsString(StandardCharsets.UTF_8);
            return RSAKey.parse(json);
        } catch (Exception e) {
            throw new IllegalStateException("failed to load demo signing JWK", e);
        }
    }
}
