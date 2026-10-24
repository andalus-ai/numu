# Supply-chain milestone 40: retryable persistence-failure classification

## Problem

The recovery-audit export worker wraps JDBC failures in `WarehouseRecoveryAuditPersistenceException` and is designed to leave transient failures recoverable through lease expiry. However, its local failure classifier recognized I/O failures and optimistic-version messages only. A typed persistence failure could therefore enter the deterministic-failure path and make an unnecessary attempt to persist a terminal `FAILED` state while the database was already unavailable.

## Changes

- Treat `WarehouseRecoveryAuditPersistenceException` as transient in the worker's failure classification.
- Extract the classification into a package-private helper and cover persistence, I/O, optimistic-version, and deterministic tenant-validation failures with unit tests.
- Preserve the lease-expiry recovery model: do not mark the export failed just because checkpoint, manifest, completion, or access-audit persistence is temporarily unavailable.
- Keep deterministic validation/integrity failures eligible for terminal failure handling.

## Recovery invariant

When the database cannot persist a checkpoint or terminal transition, durable job state may lag the artifact. The worker must not advance job state based only on an in-memory result. A later claim resumes from the last committed checkpoint and relies on idempotent chunk semantics. Manifest insertion and completion remain separately committed operations, so retries must tolerate an already-inserted matching manifest.

## Validation status

The unit test was added but Maven tests were not run in this environment. CI should run the warehouse application tests and warehouse reactor. Follow-up PostgreSQL fault-injection tests should cover failures during checkpoint update, manifest lookup/insert, job completion, and access-audit append, confirming the job remains resumable after lease expiry.
