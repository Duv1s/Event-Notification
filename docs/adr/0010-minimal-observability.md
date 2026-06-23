# 0010 — Minimal observability

## Context
The monitoring team needs to detect behavioural deviations and trace a single notification, without
standing up a full observability platform for this exercise.

## Decision
Actuator (`health`, `info`, `prometheus`) + Micrometer: a delivery counter and latency timer tagged
only with low-cardinality dimensions (`outcome`, `event_type`), plus the Resilience4j circuit-breaker
metrics. Logs are ECS-structured JSON with `trace_id`/`client_id`/`event_id` in the MDC; no PII.

## Alternatives considered
- **Full Prometheus/Grafana + OpenTelemetry + alerting:** out of scope; described in the design as the
  production target.

## Consequences
Enough to spot deviations and correlate a notification end to end. Metrics never carry `client_id` /
`subscription_id` (cardinality); per-client breakdowns live in logs/traces.