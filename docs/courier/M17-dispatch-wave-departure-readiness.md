# M17 — Dispatch-wave departure readiness gate

## Objective

Enforce dispatch readiness at the persistence boundary, not only in application/domain checks. A wave must not transition to `DEPARTED` while the database still contains missing expected parcels or load discrepancy evidence.

## Enforcement

Migration V19 installs a PostgreSQL trigger on `courier_dispatch_waves` status updates. On a transition into `DEPARTED`, it rejects the update when either:

- an expected parcel has not been scanned;
- a wave parcel has a discrepancy marker; or
- the append-only load-discrepancy ledger contains any observation for the wave.

The guard is tenant-scoped and executes in the same database transaction as the status update. It therefore protects all writers that update the wave table, including paths that do not call the hub-scan adapter.

## Deliberate authorization boundary

This milestone does **not** treat `actor_reference` or the legacy `authorized_exception_reason` text column as proof of authorization. It intentionally blocks departure when discrepancies exist, even if free-form exception text is present. An audited, permission-checked resolution/waiver workflow must be added before a legitimate exception can clear the gate. This avoids creating an authorization bypass while establishing the invariant.

The discrepancy evidence remains immutable. This migration does not delete, rewrite, or silently waive discrepancy records.

## Verification

A migration-contract test checks that V19 retains the trigger, tenant-scoped predicates, missing-scan check, discrepancy-ledger check, and fail-closed behavior. Run the courier PostgreSQL Testcontainers suite in CI to verify Flyway application and actual concurrent departure/load behavior; Maven/Testcontainers have not been run from this session.
