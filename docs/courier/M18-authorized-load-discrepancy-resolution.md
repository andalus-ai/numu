# M18 — Authorized load-discrepancy resolution

## Goal

Allow a dispatch wave to depart only after every missing-parcel condition and every durable load discrepancy has either been corrected or explicitly resolved through a permission-checked, auditable workflow. The M17 database guard remains fail-closed; free-form `authorized_exception_reason` text is never treated as authorization.

## What this change adds

- A tenant-scoped, append-only `courier_dispatch_wave_discrepancy_resolutions` ledger.
- A resolution record with wave and optional discrepancy references, parcel, controlled resolution code, reason, actor, trusted authorization-decision reference, optional evidence reference, source/idempotency key, payload fingerprint, and event timestamps.
- A database trigger that serializes resolution with departure by locking the same tenant-owned wave row.
- Database checks that require a resolution to match the referenced discrepancy's tenant, wave, parcel, and discrepancy type.
- An updated departure guard that blocks missing expected parcels without a missing-parcel waiver/removal and blocks every unresolved discrepancy record or discrepancy marker.
- An application use case and authorizer port. The authorization decision reference is returned by the trusted authorizer; it is not a field on the public request.
- A PostgreSQL adapter that persists resolutions transactionally and returns explicit accepted, replayed, and idempotency-conflict outcomes.

## Resolution codes

| Code | Meaning | Required target |
|---|---|---|
| `ACCEPT_UNEXPECTED` | Authorized operator accepts an unexpected parcel into the wave | Matching `UNEXPECTED_PARCEL` discrepancy |
| `ACKNOWLEDGE_DUPLICATE` | Operator confirms and resolves a duplicate-scan observation | Matching `DUPLICATE_SCAN` discrepancy |
| `WAIVE_MISSING` | Authorized exception for an expected parcel that cannot be physically scanned | Missing expected parcel; may reference a `MISSING_PARCEL` discrepancy |
| `REMOVE_FROM_MANIFEST` | Explicitly removes an unscanned expected parcel from this wave's departure requirements | Missing expected parcel; may reference a `MISSING_PARCEL` discrepancy |

A resolution does not delete discrepancy evidence or rewrite scan history. A later corrective workflow must append compensating evidence rather than mutate an earlier decision.

## Security boundary

The use case asks `DispatchWaveDiscrepancyResolutionAuthorizer` for a decision and only persists when the decision is allowed and has a non-empty decision reference. Production composition must bind this port to the authenticated principal and the platform's real permission/policy service. Do not implement it by trusting a request-supplied actor string, role, or permission reference.

The SQL ledger records the authorization decision reference but cannot independently determine whether an external authorization service issued that reference. Direct database write access must remain restricted to trusted application components. The migration is not a substitute for a production authorization implementation or least-privilege database credentials.

## Concurrency and idempotency

The resolution trigger locks the wave row `FOR UPDATE`, the same row whose status is changed during departure. This serializes a resolution attempt with a concurrent departure. A resolution attempted after the wave leaves `LOADING` is rejected. Repeated commands using the same tenant/source/idempotency key and same fingerprint return `REPLAYED`; a changed fingerprint returns `IDEMPOTENCY_CONFLICT`.

## Verification

Unit tests cover authorization denial and the trusted decision reference passed to persistence. Migration-contract tests cover the schema and guard definitions. These tests do not prove that the SQL migration executes successfully on PostgreSQL. Before merge, run the Courier application tests and the PostgreSQL/Testcontainers migration and concurrent departure suite in CI. No test pass is claimed by this document.
