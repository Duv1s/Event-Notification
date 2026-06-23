# 0001 — Hexagonal architecture

## Context
The solution is both a delivery engine and a query API. The reference implementation is in-memory,
but the production target is Kafka + PostgreSQL. The business rules must be swappable between those
without rewriting, and testable without a framework.

## Decision
Ports & adapters. `domain` and `application` are framework-free; `infrastructure` holds inbound (web)
and outbound (persistence, webhook) adapters. An ArchUnit test (`HexagonalArchitectureTest`) fails the
build if `domain`/`application` depend on Spring, Jackson, or `infrastructure`.

## Alternatives considered
- **Layered (controller → service → repository):** simpler, but business logic tends to leak
  framework/persistence types, and swapping adapters is harder.

## Consequences
More interfaces and explicit wiring, in exchange for fast framework-free unit tests and adapters that
swap cleanly (in-memory ↔ JPA/Kafka) behind unchanged ports.