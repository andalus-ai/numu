# M10 — Sortation and dispatch waves

## Objective

Introduce the domain boundary for sortation and controlled line-haul dispatch without coupling the courier core to a particular warehouse, scanner, or transport provider.

## Included in this milestone

- Tenant-owned dispatch-wave aggregate with origin and destination hub references.
- Expected parcel manifest before loading begins.
- Scan reconciliation outcomes: accepted, unexpected, and duplicate.
- Explicit missing-parcel calculation.
- A guarded readiness transition that blocks when any expected parcel is missing or when unexpected/duplicate scans exist.
- An explicit exception-reason path for an authorized operator workflow. The aggregate records the reason but does not itself authenticate or authorize the operator; the application boundary must enforce permissions and append an auditable actor identity.
- Departure only after readiness, monotonic timestamps, and cancellation protection after departure.
- PostgreSQL tables and indexes for wave state and parcel reconciliation.
- Domain tests for clean loads, discrepancies, exception handling, lifecycle guards, and timestamp ordering.

## Operational invariants

1. A wave cannot enter loading without at least one expected parcel.
2. The wave cannot become ready with unresolved discrepancies unless an explicit exception reason was recorded.
3. An unexpected parcel or repeated scan is retained as a discrepancy; it is not silently treated as a successful expected scan.
4. A departed wave cannot be cancelled.
5. Tenant identity participates in primary and foreign keys for wave-owned persistence.

## Deliberate follow-ups

- Application use cases and permission checks for planning, loading, exception approval, readiness, and departure.
- JDBC repository implementation with optimistic concurrency and tenant-scoped reads/writes.
- Durable scan-event ingestion/outbox integration and idempotency keys from scanner devices.
- Bag/cage/container aggregates, seal lifecycle, and parcel-to-container membership.
- Operational UI, barcode/device adapters, and integration tests against PostgreSQL migrations.
- Reconciliation against the canonical parcel inventory, including multi-piece shipments and hub-specific sortation rules.

This milestone establishes domain behavior and persistence schema; it is not a claim that device ingestion, authorization, or end-to-end production verification is complete.
