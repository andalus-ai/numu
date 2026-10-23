# Supply Chain M19 — Fenced warehouse recovery leases

## Goal

Prevent stale recovery workers from renewing or releasing a lease that has expired or has since been replaced. Recovery ownership is a time-bounded capability, not just a worker name.

## Changes

- Add claim-based lease renewal to the warehouse application repository port.
- Renew only when tenant, command key, owner, and the exact previously-issued lease deadline still match.
- Release only the exact claim, with a database-side unexpired-lease check.
- Keep the old `releaseClaim(tenantId, commandKey, workerId)` signature deprecated and fail closed in the JDBC adapter: a worker ID alone cannot distinguish an old execution from a later claim by the same worker.
- Use the database clock to decide whether a persisted lease is expired during claim, renewal, and release.
- Normalize generated lease deadlines to microsecond precision to match PostgreSQL timestamp storage and equality checks.

## Required caller behavior

1. Keep the `WarehouseExecutionClaim` returned by `claimRecoverable`.
2. Before doing more work near expiry, call `renewClaim(claim, lease)` and replace the old claim with the returned claim.
3. Release using `releaseClaim(claim)` only after handling is complete.
4. If renewal or release throws `OptimisticLockException`, stop processing that attempt as the current owner. Do not retry the side effect under the stale claim; reacquire and re-read the durable journal.

A claim deadline is the fencing value. Once renewed, the old claim object cannot renew or release the new lease, even when the same worker identity receives it again later.

## Boundaries and validation

- The database must have the warehouse recovery claim columns introduced by the recovery schema migrations.
- PostgreSQL integration tests should cover expired release, expired renewal, a stale claim after re-claim by the same worker ID, successful renewal followed by release of the previous claim, and successful release of the current claim.
- This milestone does not make external side effects exactly-once. Inventory and warehouse effects still need idempotent commands and reconciliation.
- Maven/PostgreSQL tests were not run as part of this change; CI or a disposable PostgreSQL test environment must validate SQL behavior before merge.
