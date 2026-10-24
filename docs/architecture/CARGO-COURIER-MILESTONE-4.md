# Cargo & Courier — Milestone 4: Hub Operations and Transfer Manifests

## Scope

M4 begins the operational hub-to-hub movement layer. It introduces a tenant-owned transfer manifest that declares the shipments moving between an origin hub and a destination hub. Physical scan events continue to be represented by the M3 tracking event model.

## Domain lifecycle

- A transfer starts as a draft with distinct origin and destination hubs.
- Shipment membership is draft-only and duplicate shipment IDs are rejected.
- An empty transfer cannot be sealed.
- The lifecycle is DRAFT → SEALED → DEPARTED → ARRIVED; draft/sealed transfers may be cancelled.
- Transition timestamps are monotonic and invalid timestamps do not partially mutate the aggregate.

## Persistence

- Flyway V5 adds tenant-scoped transfer manifests and shipment membership.
- Composite foreign keys prevent cross-tenant hub, transfer, and shipment references.
- Manifest and all shipment membership rows are persisted in one transaction.
- Lifecycle-aware upsert guards reject stale writes that would regress a departed/arrived transfer.
- Tenant-local manifest reference uniqueness and timeline/status indexes are database-enforced.

## Follow-on acceptance work

- Prevent conflicting active transfers for the same shipment and add load reconciliation.
- Emit M3 tracking events on seal/depart/arrival through an application use case and transactional outbox.
- Add hub scan intake, sortation lanes/bags, route planning, load reconciliation, and exception handling.
- Add PostgreSQL integration tests and run the Maven reactor in CI; tests are not claimed as passing from this environment.
