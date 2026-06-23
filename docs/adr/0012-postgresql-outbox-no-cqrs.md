# 0012 — PostgreSQL with the Outbox pattern, no CQRS (production)

## Context
The system must "store the final delivery information", and the self-service queries are relational
(`client_id` + `occurred_at` range + `delivery_status`). (Design-only; the reference implementation is
an in-memory adapter behind the same ports.)

## Decision
PostgreSQL as the primary store (composite index on `(client_id, occurred_at, id)`, `JSONB` payload,
time partitioning for retention) plus a transactional **Outbox** drained by a relay/CDC. Reads use the
same store (read replicas if needed); no separate read model.

## Alternatives considered
- **NoSQL (Cassandra/Dynamo):** the queries are relational and the volume does not justify it.
- **CQRS read model:** unjustified complexity for these query shapes.

## Consequences
At-least-once publication to the broker, consistent with the database (no dual-write). Swapping the
in-memory adapter for a JPA/PostgreSQL one leaves the domain untouched.