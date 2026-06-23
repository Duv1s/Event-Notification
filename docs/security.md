# Security analysis (OWASP)

The API is assumed to be **consumed publicly over the internet**. This analysis uses only public
sources: the [OWASP Top 10 2021](https://owasp.org/Top10/) and the
[OWASP API Security Top 10 2023](https://owasp.org/API-Security/editions/2023/en/0x00-header/).
It anchors on the 2021 Top 10 and cross-references the API Security Top 10 2023, which is the more
appropriate list for an API.

Three vulnerabilities are treated **formally** (threat · mitigation · where it lives in the code);
the rest are covered as additional defence-in-depth controls.

---

## 1. A01:2021 Broken Access Control — [API1:2023 BOLA] / [API5:2023 BFLA]

**Threat.** A client guesses or enumerates `notification_event_id`s and reads or replays another
tenant's notifications (Broken Object Level Authorization), or calls an operation it lacks permission
for (Broken Function Level Authorization).

**Mitigation.**
- The tenant is taken from the JWT `client_id` claim (`JwtClientIdResolver`), **never** from a query
  or path parameter.
- The client-facing port `NotificationRepository` only exposes tenant-scoped reads
  (`findByIdAndClientId`, `search` over a `NotificationQuery` that carries the `ClientId`); there is no
  `findById` without a `ClientId`, so a cross-tenant read is structurally impossible.
- Missing **or** foreign notifications return a **uniform 404** (`GlobalExceptionHandler` →
  `NotificationNotFoundException`), so existence is not leaked (anti-enumeration).
- The internal, tenant-less delivery read is segregated into `DeliveryNotificationStore`, off the
  client-facing port, so it can never be reached from the API.
- Function-level access is enforced with scopes via `@PreAuthorize` (`SCOPE_notifications:read` on
  the GETs, `SCOPE_notifications:replay` on replay).

**Where.** `JwtClientIdResolver`, `NotificationRepository`, `NotificationController` (`@PreAuthorize`),
`GlobalExceptionHandler`, `DeliveryNotificationStore`.

---

## 2. A10:2021 Server-Side Request Forgery — [API7:2023 SSRF]

**Threat.** The webhook destination URL is client-controlled. An attacker registers a URL pointing at
an internal service or the cloud metadata endpoint (`169.254.169.254`) and uses the delivery engine as
a proxy to reach them.

**Mitigation.** `SsrfGuard` runs before **every** delivery and:
- allows only `https` on port `443` (rejects `http`, other ports/schemes);
- resolves DNS and rejects the target if any resolved address is loopback / any-local / link-local
  (incl. the metadata address) / site-local (private) / multicast / IPv6 unique-local; IPv4-mapped
  IPv6 is normalised and caught by the same rules;
- does not follow `3xx` redirects (a redirect to an internal address is treated as a non-retryable
  config failure).

A residual TOCTOU (validate-then-connect rather than pinning the resolved IP) is documented and
accepted for the in-memory demo; production pins the validated IP.

**Where.** `SsrfGuard`, `ResilientWebhookClient` (redirects disabled on the HTTP client,
non-retryable handling), `WebhookUrl` (enforces `https` at the type level).

---

## 3. A07:2021 Identification & Authentication Failures — [API2:2023 Broken Authentication]

**Threat.** Forged, expired, or wrong-audience tokens are accepted; a token minted for another service
is replayed against this API; a token without a tenant is treated as valid.

**Mitigation.** OAuth2 **JWT resource server** (`SecurityConfig`): tokens are verified for **signature**
(against a JWKS — local for the demo, the IdP's `jwk-set-uri` in production), **`iss`**, **`aud` (this
API)**, **`exp`/`nbf`**, and a **required `client_id` claim**; any failure is `401`. Validation is never
disabled. The session policy is stateless; tokens are bearer JWTs (no server session to fixate).

**Where.** `SecurityConfig` (`jwtDecoder` with `JwtTimestampValidator`, `JwtIssuerValidator`,
audience and `client_id` validators), enforced by the resource-server filter chain.

---

## Additional controls (defence in depth)

| OWASP 2021 | API Top 10 2023 | Control | Where |
|------------|-----------------|---------|-------|
| A02 Cryptographic Failures | — | HTTPS only + TLS cert verification always on; HMAC-SHA256 signature over `timestamp.body`, per-subscription secret | `ResilientWebhookClient`, `WebhookConfiguration` |
| A04 Insecure Design | API4:2023 Unrestricted Resource Consumption | Per-client rate limiting, two tiers (read vs. the costlier replay), `429` + `Retry-After` + `RateLimit-*` | `RateLimitInterceptor`, `WebConfig` |
| A05 Security Misconfiguration | API8:2023 | Stateless config, CSRF disabled for the token API, CORS not enabled (M2M), Spring Security default response headers; demo secrets are clearly-marked non-production defaults | `SecurityConfig`, `application.yml` |
| A09 Logging & Monitoring Failures | API9:2023 Improper Inventory / logging | Structured JSON logs with `trace_id`/`client_id`/`event_id`; **never** log `content`, signature, secret, token or other PII | `RequestCorrelationFilter`, `AsyncDeliveryDispatcher`, observability config |
| A06 Vulnerable & Outdated Components | — | SCA + SAST in CI (Dependabot + CodeQL) | `.github/dependabot.yml`, `.github/workflows/codeql.yml` |
| A08 Software & Data Integrity | API ↔ | Signed webhook payloads (HMAC), versioned signature scheme (`v1`) enabling secret rotation | `ResilientWebhookClient` |

## Input validation

All query inputs are validated and rejected with `400` (RFC 7807): ISO-8601 dates, the public
`delivery_status` enum, `page_size` (1..100, Bean Validation), and the opaque base64 cursor. Request
DTOs are explicit and Jackson rejects unknown properties, mitigating mass assignment
([API6:2023 BOPLA]). See `NotificationController`, `CursorCodec`, `GlobalExceptionHandler`.

## Notes

- Demo authentication uses a **local JWKS**; production sets `OAUTH2_JWK_SET_URI` to the IdP.
- Actuator `health`/`info`/`prometheus` are public **for the demo only**; in production they are
  restricted by network/ACL.