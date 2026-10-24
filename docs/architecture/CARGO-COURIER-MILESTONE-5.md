# Cargo & Courier — Milestone 5: Courier Dispatch and Proof of Delivery

## Scope

M5 establishes the delivery execution aggregate and persistence port. It builds on M1-M4: tenant-scoped shipments, tracking events, hub transfer manifests, and stable tracking timelines.

## Domain lifecycle

- A delivery task assigns one booked shipment to a courier reference and records the assigning actor.
- Lifecycle: ASSIGNED → OUT_FOR_DELIVERY → DELIVERY_ATTEMPTED (repeatable) → DELIVERED, or FAILED.
- Only an unstarted assignment may be cancelled.
- Delivery completion requires a recipient name and a protected proof-reference token. Raw photos/signatures are not stored in the domain database; the reference must resolve through a tenant-authorized evidence service.
- Outcome timestamps must be monotonic and persisted state is reconstituted with lifecycle invariant checks.

## Persistence and isolation

- Flyway V6 creates tenant-scoped delivery task records, shipment foreign keys, status/time constraints, and indexes for courier queues and shipment history.
- A partial unique index prevents more than one active assignment for a shipment.
- JDBC persistence validates that the shipment belongs to the tenant and is BOOKED before assignment or update.
- Tenant-scoped lookup, courier queue queries, and active-assignment lookup are provided.
- Persisted lifecycle transitions reject stale regressions.

## Verification status

Domain and PostgreSQL Testcontainers tests have been added for the delivery lifecycle, proof-of-delivery persistence, tenant isolation, and rejection of draft shipments. They have not been executed from this environment; CI/Maven results are required before merge.

## Remaining before production exposure

- Add an outbox dispatcher that claims rows safely with `FOR UPDATE SKIP LOCKED`, retries failures, and projects delivery milestones into M3 tracking events idempotently.
- Add authenticated courier identity and authorization policy; courier references are not an authentication mechanism.
- Add offline command idempotency, delivery geolocation/time evidence, media authorization/retention, recipient privacy rules, and signature/photo capture through a protected media service.
- Add reassignment/route optimization, multi-stop tours, failed-delivery retry policy, and COD collection/reconciliation.
