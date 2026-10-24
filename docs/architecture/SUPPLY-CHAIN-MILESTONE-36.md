# Supply-chain milestone 36: recovery-audit worker failure classification

## Problem
The JDBC adapters historically wrapped SQL failures in generic `IllegalStateException`. The export worker treats most unexpected runtime exceptions as deterministic and terminal, so a temporary database outage while checkpointing or persisting the archive manifest could incorrectly mark a resumable export as failed.

## Changes
- Introduce `WarehouseRecoveryAuditPersistenceException` as a typed signal for JDBC persistence failures.
- Use the typed exception for export job, access-audit, and archive-manifest JDBC operations.
- Keep the worker's lease-based retry path for I/O and typed persistence failures rather than marking the export terminal.
- If a deterministic processing failure occurs while the database is unavailable during failure recording, preserve the original error and leave the lease to expire rather than masking it or assuming a terminal transition succeeded.
- Allow the export-job domain model to reclaim a `VERIFYING` job after a process interruption while retaining its artifact metadata.
- Add regression coverage for reclaiming interrupted verification and preserving persistence failure causes.

## Recovery guarantees and limits
The worker remains at-least-once across process interruptions; artifact chunk IDs and the durable artifact store must remain idempotent. A transient database failure can delay progress until lease expiry. This milestone does not add retry backoff, alerting/metrics, or real PostgreSQL/Testcontainers integration tests. Those remain follow-up work.

## Validation
Automated Maven tests and PostgreSQL integration tests were not run in this environment. CI must validate module compilation and persistence behavior before production enablement.