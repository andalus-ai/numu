# Supply Chain Milestone 7 — Durable execution recovery contract

## Goal

Make the cross-context failure window from M6 explicit and recoverable without pretending warehouse and inventory can participate in one local transaction.

## Included

- A versioned `WarehouseExecutionAttempt` records tenant, command key, canonical payload fingerprint, work order/line, operation, progress state, retry count, timestamps, and diagnostic error.
- The state machine records `PREPARED -> INVENTORY_POSTED -> WAREHOUSE_COMMITTED`; uncertain failures move to `NEEDS_RECONCILIATION`.
- A retry returns to `PREPARED` only from `NEEDS_RECONCILIATION`, increments the attempt count, and retains the original command identity.
- `WarehouseExecutionAttemptRepository` defines tenant-scoped durable storage, optimistic compare-and-set transitions, duplicate-key protection, and bounded recovery scans.
- `WarehouseExecutionRecoveryService` enforces payload identity and exposes state transitions and bounded recovery polling.
- Unit tests cover invalid transition order, retry behavior, and payload-key conflicts.

## Required adapter invariants

A production adapter must:

1. Enforce unique `(tenant_id, command_key)` and persist the fingerprint in the same transaction as the initial journal record.
2. Persist transitions using compare-and-set on `version`; stale writers must fail rather than overwrite newer progress.
3. Return only tenant-scoped records and page/limit recoverable work.
4. Persist journal state across process restart and expose operational metrics for attempt age, retry count, and reconciliation backlog.
5. Never infer that inventory was not posted merely because the warehouse-side operation timed out.
6. Reuse the same inventory idempotency key and identical payload during recovery. A conflicting payload is a hard error requiring operator review.
7. Mark `WAREHOUSE_COMMITTED` only after the warehouse repository confirms its aggregate, receipt, and outbox commit.

## Recovery guidance

- `PREPARED`: inspect inventory by the stable command key before attempting any non-idempotent mutation; never assume no movement exists.
- `INVENTORY_POSTED`: replay the same warehouse command key/payload, relying on warehouse command receipt idempotency.
- `NEEDS_RECONCILIATION`: preserve evidence, inspect inventory and warehouse receipts, and retry only after resolving ambiguity.
- `WAREHOUSE_COMMITTED`: terminal; do not reissue stock movements.

## Explicit limitations

This milestone adds the recovery model and persistence port, not a database adapter or migration. The repository contract alone is not production durability. The current M6 inventory idempotency adapter remains in-memory and single-process. A production rollout is blocked until durable inventory command receipts and warehouse aggregate + receipt + outbox transactions are implemented and tested against a real database. Tests/CI must be run before merge; no green result is claimed here.

## Next milestone

Implement a concrete database adapter and migration for the warehouse aggregate, command receipt, outbox, and execution journal; then add database-backed concurrency/crash-recovery tests and connect the journal to the M6 coordinator.
