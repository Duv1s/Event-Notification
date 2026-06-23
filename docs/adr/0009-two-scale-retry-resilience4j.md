# 0009 — Two-scale retry strategy with Resilience4j

## Context
Transient delivery failures should be retried, but a thread cannot block for the minutes/hours a
backoff schedule may span.

## Decision
One full-jitter exponential-backoff strategy at two scales. The reference implementation runs the
**in-process** scale with Resilience4j: retry (max 4, 500 ms × 2, cap 5 s, honouring `Retry-After`) as
the outer decorator, a **per-destination circuit breaker** inside. The design elevates it to a
**spaced/distributed** scale (broker retry topics + dead-letter topic) over ~24 h.

## Alternatives considered
- **Only in-process retries:** cannot span hours.
- **Only spaced retries:** slower for transient blips that a few hundred ms would clear.

## Consequences
In the reference implementation a persistently-retryable delivery lands in `RETRYING` and stays there
(the spaced layer that would advance it is design-only) — see the README demo caveat.