# M21 — Proof-of-delivery tracking projection

## Objective

Consume the M20 transactional outbox into the existing append-only courier tracking timeline. This gives the tracking API a durable `DELIVERED` fact without adding a second write to the POD request path.

## Included

- `JdbcProofOfDeliveryTrackingProjector` claims a bounded batch of eligible POD events with PostgreSQL `FOR UPDATE SKIP LOCKED`.
- The tracking event insert and outbox acknowledgement occur in one database transaction.
- Tracking idempotency key is `POD:<proofId>`; an existing key is accepted only when shipment, event type, and occurrence time match.
- Missing or malformed shipment references remain unpublished and are retried with bounded exponential backoff (15 seconds up to one hour).
- Shipment lookup is scoped by tenant and normalized shipment reference.

## Delivery and failure semantics

This is an at-least-once projection with an idempotent database sink, not a broker publisher. A worker crash rolls back its transaction and releases row locks. Failed rows record a bounded error and next-attempt time; successful projection and `published_at` acknowledgement commit atomically. Unknown event types are left for a separate handler rather than silently marked published.

## Explicit boundary

This milestone adds a `DELIVERED` tracking fact only. It does **not** mutate `courier_shipments.status` or `courier_delivery_tasks.status`; those lifecycle transitions require a dedicated use case and concurrency policy. The scheduled worker must be wired by the runtime before events are projected.

## Validation gates

The source-contract tests are authored but have not been run in this session. CI must run the Courier adapter Maven tests and PostgreSQL integration tests covering two concurrent workers, failed shipment lookup/retry, idempotent replay, conflicting idempotency key, and transaction rollback. Monitor unpublished event age, attempt counts, and repeated tenant/reference lookup failures.
