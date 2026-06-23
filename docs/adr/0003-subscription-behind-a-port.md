# 0003 — `Subscription` behind a port, CRUD out of scope

## Context
Delivery needs each client's URL, HMAC secret, event-type filter and state. Managing subscriptions is
a separate concern from delivering notifications.

## Decision
`Subscription` is a first-class domain object read through `SubscriptionRepository`; its CRUD is out
of scope and assumed pre-existing (seeded into an in-memory adapter from `subscriptions.json`).

## Alternatives considered
- **Implement subscription CRUD here:** out of scope for this service and conflates two bounded contexts.

## Consequences
Replay and delivery resolve the *current* URL/secret/state by `subscription_id`. Production swaps the
in-memory adapter for the real subscriptions service behind the same port.