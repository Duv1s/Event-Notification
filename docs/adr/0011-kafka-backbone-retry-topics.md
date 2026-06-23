# 0011 — Kafka backbone with retry topics and DLT (production)

## Context
At ~20k events/s peak the engine must decouple ingestion from delivery, support replay, and keep
per-client ordering where possible. (Design-only; the reference implementation is in-memory + `@Async`.)

## Decision
Kafka as the backbone, partitioned by `client_id`. Spaced retries are modelled with staggered
**retry topics** (increasing delays) and a **dead-letter topic** on exhaustion.

## Alternatives considered
- **A delay-native broker (RabbitMQ delayed-exchange / a managed queue with delay + DLQ):** simpler
  scheduling, but lower throughput and weaker per-client ordering.

## Consequences
Kafka has no native delayed delivery, hence the retry topics. Per-partition ordering is intentionally
relaxed by retries (a failed event is re-attempted later while newer ones proceed), which is why the
contract is at-least-once with no ordering guarantee.