# Supply-chain milestone 40: retryable persistence-failure classification

## Problem

The recovery-audit export worker already wraps JDBC failures in `WarehouseRecoveryAuditPersistenceException` and is designed to leave transient failures recoverable through lease expiry. However, the worker's local failure classifier recognized I/O failures and optimistic-version messages only. A typed persistence failure could therefore enter the deterministic-failure path and make an unnecessary attempt to persist a terminal `FAILED` state while the database was already unavailable.

## Changes

- Treat `WarehouseRecoveryAuditPersistenceException` as transient in the worker's failure classification.
- Preserve the existing lease-expiry recovery model: do not mark the export failed just because checkpoint, manifest, completion, or access-audit persistence is temporarily unavailable.
- Keep deterministic validation/integrity failures eligible for terminal failure handling.

## Recovery invariant

When the database cannot persist a checkpoint or terminal transition, the durable job state may lag the artifact. The worker must not advance the job state based only on an in-memory result. A later claim must resume from the last committed checkpoint and rely on the artifact store's idempotent chunk semantics. Manifest insertion and completion remain separately committed operations, so retries must continue to tolerate an already-inserted matching manifest.

## Validation status

Maven tests and PostgreSQL fault-injection tests were not run in this environment. CI should test failures during checkpoint update, manifest lookup/insert, job completion, and access-audit append, confirming the original persistence error is propagated and the job remains resumable after lease expiry. A follow-up integration milestone should exercise these crash windows against a real PostgreSQL instance.
