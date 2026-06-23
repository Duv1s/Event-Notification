# 0005 — Replay only from `FAILED`

## Context
The API must "re-send a notification when its delivery has definitely failed". The dataset only has
`completed` and `failed`.

## Decision
A single terminal failure state, `FAILED` = retries exhausted, definitive and replay-eligible. Replay
is allowed only from `FAILED`; from `COMPLETED`/`PENDING`/`IN_PROGRESS` it returns `409`. The
`failed` dataset value maps directly to `FAILED`.

## Alternatives considered
- **A separate `EXHAUSTED`/`DEAD` terminal state:** adds no value and makes the mapping of the
  dataset's `failed` ambiguous. The transient condition is already captured by `RETRYING`.

## Consequences
A clear replay precondition. The `FAILED → DELIVERING` claim is performed atomically
(`claimForReplay`) to guard against concurrent double-replay.