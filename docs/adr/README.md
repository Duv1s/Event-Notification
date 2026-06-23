# Architecture Decision Records

Short ADRs (Context · Decision · Alternatives considered · Consequences). They expand on section 13
of [`../system-design.md`](../system-design.md).

| # | Decision |
|---|----------|
| [0001](0001-hexagonal-architecture.md) | Hexagonal architecture |
| [0002](0002-notification-single-aggregate.md) | `Notification` as the single aggregate |
| [0003](0003-subscription-behind-a-port.md) | `Subscription` behind a port, CRUD out of scope |
| [0004](0004-delivery-date-as-occurred-at.md) | `delivery_date` as a proxy for `occurred_at` |
| [0005](0005-replay-only-from-failed.md) | Replay only from `FAILED` |
| [0006](0006-at-least-once-hmac-idempotency.md) | At-least-once delivery with HMAC and idempotency |
| [0007](0007-ssrf-guard-on-every-delivery.md) | SSRF guard on every delivery |
| [0008](0008-oauth2-tenancy-at-the-port.md) | OAuth2 resource server, tenancy enforced at the port |
| [0009](0009-two-scale-retry-resilience4j.md) | Two-scale retry strategy with Resilience4j |
| [0010](0010-minimal-observability.md) | Minimal observability |
| [0011](0011-kafka-backbone-retry-topics.md) | Kafka backbone with retry topics and DLT (production) |
| [0012](0012-postgresql-outbox-no-cqrs.md) | PostgreSQL with the Outbox pattern, no CQRS (production) |
| [0013](0013-java-26-spring-boot-4.md) | Java 26 + Spring Boot 4.1 (stack / runtime) |