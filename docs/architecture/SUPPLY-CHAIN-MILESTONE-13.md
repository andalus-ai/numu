# Supply Chain M13 — Recovery diagnostics and command validation

## Goal

Make warehouse recovery failures visible to the host application and reject malformed
inventory commands before constructing fingerprints or durable recovery payloads.

## Changes

- Added an optional `WarehouseExecutionRecoveryWorker.FailureListener`; existing constructors
  remain source-compatible and use a no-op listener.
- Added immutable `RecoveryFailure` context with worker ID, tenant ID, command key, operation,
  journal status, and the original runtime exception.
- A recovery failure keeps its durable lease until expiry, avoiding a tight retry loop. The
  worker reports the failure and continues processing the claimed batch.
- A broken observability listener cannot abort recovery batch processing.
- Receiving and picking now validate required text fields and decimal values before calling
  `toPlainString()`, hashing the command, or building the persisted recovery payload.
  Receiving quantity must be positive and unit cost non-negative; picking quantity must be
  positive and reservation ID must be present.

## Host integration

Provide a listener when creating the recovery worker to connect the callback to the host's
structured logger, metrics, alerting, or reconciliation queue. Suggested counters include
claimed, succeeded, failed, and lease-expired/retried work; use operation and status as bounded
labels. Do not use tenant IDs, command keys, item IDs, or exception messages as metric labels,
because they have high cardinality and may contain business-sensitive data.

The callback is intentionally framework-neutral. The host is responsible for scheduling the
worker, exporting metrics, alert thresholds, and durable operator-facing reconciliation records.
The no-op default preserves compatibility but does not provide operational visibility by itself.

## Reliability boundaries

- The recovery worker is at-least-once. Downstream inventory and warehouse commands must retain
  durable idempotency receipts scoped to tenant and command key.
- A listener callback is best-effort and must not be the only record of a failure. The durable
  attempt journal and lease remain the recovery source of truth.
- A failed handler or failed claim release leaves the lease in place until expiry; the next
  worker can retry after expiry.
- Legacy journal entries without M12 payloads cannot be automatically replayed and require
  explicit reconciliation.
- This milestone does not claim that inventory, journal, and warehouse state form a distributed
  transaction. PostgreSQL restart/concurrency tests and a full Maven build remain required
  before production rollout.

## Tests added

- Successful handling still releases the claim.
- Handler failure leaves the lease and emits structured failure context.
- A throwing failure listener does not interrupt batch result accounting.
- Constructor and command argument validation are covered by unit tests.
