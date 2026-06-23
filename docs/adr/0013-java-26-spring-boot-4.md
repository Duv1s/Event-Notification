# 0013 — Java 26 + Spring Boot 4.1 (stack / runtime)

## Context
A greenfield personal exercise whose stated purpose is to revisit modern Java and Spring Boot.

## Decision
Build on **Java 26 + Spring Boot 4.1** (Spring Framework 7). Library support on Boot 4 was verified
empirically rather than assumed: Resilience4j via the dedicated `resilience4j-spring-boot4` (2.4.0)
module, springdoc via the 3.0.x line, `@WebMvcTest` via `spring-boot-starter-webmvc-test`.

## Alternatives considered
- **Java 21 LTS + Spring Boot 3.3.x:** more mature ecosystem and long-term support. A production
  service would likely pick this LTS pair; it was chosen against here only to exercise the latest stack.

## Consequences
Some libraries needed Boot-4-specific artifacts and the JDK-26 toolchain is auto-provisioned (Foojay).
Records and pattern matching are used freely. The trade-off is bleeding-edge maturity for current APIs.