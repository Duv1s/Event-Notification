# 0006 — At-least-once delivery with HMAC and idempotency

## Context
Webhook deliveries can be duplicated (in-process retries, spaced retries, manual replay), and the
receiver must verify authenticity and de-duplicate.

## Decision
At-least-once delivery, no ordering guarantee. Each request is signed with **HMAC-SHA256 over
`timestamp + "." + body`** (`X-Signature: t=<ts>,v1=<hex>`, secret per subscription). A stable
**`X-Event-Id` (= `event_id`)** lets the receiver de-duplicate across retries and replays. The inbound
replay endpoint also accepts the standard `Idempotency-Key`.

## Alternatives considered
- **Exactly-once:** impractical over webhooks.
- **Signing only the body:** leaves the timestamp unauthenticated, enabling replay attacks.

## Consequences
Receivers must be idempotent and order by `occurred_at + event_id` rather than by arrival order. The
signature scheme is versioned (`v1`) to allow rotation.