# 0004 — `delivery_date` as a proxy for `occurred_at`

## Context
The API filters by *event creation date*, but the dataset only carries `delivery_date` (a delivery
timestamp). There is no real event-creation time in the source.

## Decision
Seed `occurred_at` from `delivery_date` (documented assumption) and filter on `occurred_at`, not on
the delivery timestamp.

## Alternatives considered
- **Filter on `delivery_date`:** wrong semantics — it conflates when the event happened with when it
  was delivered.

## Consequences
In this dataset the two values collapse. A production producer would carry the true event time, and
the filter contract already targets it.