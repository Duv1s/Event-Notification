# 0002 — `Notification` as the single aggregate

## Context
This service does not own the platform events; each dataset row is a delivery record (it has
`delivery_status` and `delivery_date`).

## Decision
Model one aggregate, `Notification` (identity = `EventId` = `event_id`), embedding the event data
(`event_type`, `content`, `occurred_at`) plus an ordered `DeliveryAttempt` history. No separate
`Event` entity. The relationship is 1:1 in the dataset (1:N fan-out in the design).

## Alternatives considered
- **Separate `Event` and `Notification` entities:** unnecessary given the data and adds mapping.

## Consequences
A simpler model. The notification stores its originating `subscription_id`, so fan-out (one
notification per matching active subscription) can be added without restructuring.