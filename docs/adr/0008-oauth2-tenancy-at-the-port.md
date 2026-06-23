# 0008 — OAuth2 resource server, tenancy enforced at the port

## Context
The API is public on the internet and must guarantee strict per-client isolation (anti-BOLA).

## Decision
An OAuth2 **JWT resource server** validates signature, `iss`, `aud`, `exp`/`nbf` and a required
`client_id` claim; method security enforces the `notifications:read` / `notifications:replay` scopes.
The tenant comes from the `client_id` claim (`JwtClientIdResolver`), never from a request parameter.
The client-facing `NotificationRepository` exposes no read without a `ClientId`
(`findByIdAndClientId`); missing/foreign notifications return a uniform `404`.

## Alternatives considered
- **API keys:** no native expiry or scopes.
- **`client_id` as a query/path parameter:** trivially exploitable BOLA.

## Consequences
A cross-tenant read is structurally impossible from the client API. The internal delivery path uses a
segregated `DeliveryNotificationStore` (no tenant), kept off the client-facing port.