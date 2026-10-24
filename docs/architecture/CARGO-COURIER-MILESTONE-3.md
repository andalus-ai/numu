# Cargo & Courier — Milestone 3: Tracking and Chain of Custody

## Scope

M3 introduces immutable tracking facts and the append-only timeline contract. It is intentionally independent of hub routing/sortation (M4) and courier dispatch/proof of delivery (M5).

## Domain contract

- Every tracking event is owned by a tenant and references a shipment; parcel-level events may reference a parcel from the same tenant.
- Event type is a stable operational vocabulary spanning pickup, hub arrival/departure, linehaul, delivery attempt, delivery, exception, and returns.
- `occurredAt` represents when the operation happened; `recordedAt` represents when the system recorded it. Recording before occurrence is rejected in this initial contract.
- `source` and `idempotencyKey` are mandatory. A scanner retry must not create a second event.
- Location, actor, source, and notes are snapshots, not live references to mutable directory data.

## Persistence

- Flyway V4 adds a tenant-scoped append-only tracking table.
- Composite foreign keys enforce same-tenant shipment and parcel references.
- Unique `(tenant_id, idempotency_key)` provides the database concurrency guard.
- Shipment and parcel foreign keys use RESTRICT so tracking history cannot be erased by deleting operational records.
- Timeline indexes support shipment and parcel history queries.
- Timeline pagination uses an exclusive composite cursor (occurrence timestamp + event ID), avoiding skipped events when multiple scans share a timestamp.

## Follow-on acceptance work

- Wire scan ingestion through an application command with authenticated actor context.
- Define correction/supersession events rather than editing historical facts.
- Implement public tracking-number resolution, privacy filtering, retention, and API rate limiting before customer exposure.
- M4/M5 will emit hub, linehaul, dispatch, and delivery events through this contract.
- Run the Maven reactor and PostgreSQL Testcontainers suite in CI; tests are not claimed as passing by this change.
