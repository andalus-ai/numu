# Courier M9 — Pickup Scheduling and First-Mile Collection

## Scope
M9 introduces a tenant-scoped pickup request tied to an already-booked shipment. It records merchant reference, pickup address snapshot, requested time window, requester, courier assignment, collection evidence reference, and failed-attempt reason.

## Lifecycle
- REQUESTED: accepted for a future pickup window.
- ASSIGNED: assigned to a courier for collection.
- COLLECTED: collection recorded with an opaque evidence reference.
- FAILED: attempt failed with a reason and may be reassigned.
- CANCELLED: terminal cancellation before collection.

Pickup windows require positive duration and cannot start in the past when created. Domain timestamps are monotonic. Collection requires assignment and evidence. Raw images and signatures are not stored in the domain.

## Tenant and persistence boundaries
Repository calls and shipment lookups are tenant-scoped; the database enforces a tenant-composite shipment foreign key, tenant-local pickup reference, and one pickup request per shipment. Guarded PostgreSQL updates reject invalid transitions and stale writes. Pickup address is location data and must follow the deployment's retention policy.

## Verification
Domain tests cover assignment preconditions, evidence requirements, reassignment after failure, invalid windows, and backwards timestamps. Run courier domain tests, PostgreSQL Testcontainers tests, and Maven reactor verification in CI before production use.

## Follow-up
Add pickup batch manifests, capacity-aware time-window planning, barcode intake, transactional pickup tracking events, and outbox-driven notifications in subsequent milestones.
