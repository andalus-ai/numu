# M16 — Durable load discrepancy intake

## Objective

A parcel observation that conflicts with a dispatch-wave manifest must be retained as operational evidence without being misrepresented as a successful line-haul load. M16 adds an explicit `LOAD_DISCREPANCY` scan path, separate from `DISPATCH_LOADED`.

## Behavior

- Discrepancy commands require a dispatch-wave reference and a nonblank reason (up to 500 characters).
- The adapter locks the tenant-scoped wave and accepts discrepancy intake only while it is LOADING at the origin hub.
- A parcel absent from the wave ledger is added as `expected=false`, `scan_count=1`, and `discrepancy=UNEXPECTED`.
- A parcel already scanned as expected is recorded as a duplicate discrepancy; its scan count is incremented and discrepancy marked `DUPLICATE`.
- Each accepted discrepancy creates a durable `courier_dispatch_wave_discrepancies` record, scan receipt, `LOAD_DISCREPANCY` tracking fact, and outbox event in the same transaction.
- A discrepancy does not create `LOADED_FOR_LINEHAUL`. Ordinary `DISPATCH_LOADED` continues to reject parcels that are not registered as expected.
- Tenant-scoped foreign keys, a tenant-scoped idempotency key, and wave-row locking prevent cross-tenant evidence writes and serialize classification against load scans.

## Migration

V18 adds `discrepancy_reason` to scan receipts, expands the scan and tracking event vocabularies, updates the custody-field constraint for the new scan type, and creates a dedicated append-only discrepancy ledger with indexes.

## Authorization boundary

This is evidence intake, not permission to override a dispatch manifest. A discrepancy is accepted only as an observation. This milestone does not authorize a user to change an expected parcel, waive a manifest discrepancy, depart a wave with unresolved discrepancies, or approve an exception. Those actions require a separate authorization service and audited command path; do not infer authority from `actor_reference`.

## Verification status

A PostgreSQL Testcontainers test was added for unexpected parcel intake, durable discrepancy reason, and the distinct tracking event. Maven/Testcontainers were not run in this environment. CI must verify compilation, migration compatibility, the tracking event CHECK constraint, and atomic rollback/concurrent duplicate behavior before merge.
