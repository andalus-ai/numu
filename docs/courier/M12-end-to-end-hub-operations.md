# M12 — End-to-End Hub Operations

## Goal

Provide the durable operational boundary that joins first-mile collection, hub intake, sortation, dispatch loading, sealed containers, inter-hub movement and receiving without making scanner retries create duplicate business facts.

## Included in this milestone slice

- Canonical scan vocabulary for pickup collection, hub intake, sortation, dispatch loading, sealing, dispatch, receiving and exception recording.
- Immutable, tenant-scoped `HubScanCommand` with validated source, idempotency key, payload fingerprint and occurrence/receipt timestamps.
- Application port `HubOperationsStore` with explicit outcomes for accepted scans, safe replays and idempotency conflicts.
- PostgreSQL V15 scan idempotency ledger and append-only operational decision audit table.
- Tenant-scoped uniqueness and foreign-key boundaries, timestamp checks, and indexes for hub queues and shipment timelines.

## Required transaction semantics for the PostgreSQL adapter

1. Resolve the shipment under the command tenant; never infer tenant identity from the supplied shipment reference.
2. Attempt to claim `(tenant_id, source, idempotency_key)`.
3. If the key exists and its canonical payload fingerprint matches, return `REPLAYED` without incrementing scan counts, changing aggregate state, appending tracking events, or emitting outbox messages.
4. If the key exists with a different fingerprint, return `IDEMPOTENCY_CONFLICT`; do not overwrite the original receipt.
5. For a new key, validate the requested transition against current dispatch wave/container/pickup state, then persist receipt, tracking facts, and outbox messages in one database transaction.
6. Operational exception approvals/overrides require authenticated authorization before writing audit evidence. The audit row is evidence, not an authorization mechanism.
7. Outbox publishers remain at-least-once; consumers must deduplicate using the stable event key. A scan receipt alone is not proof that downstream delivery succeeded.

## Integration boundaries

- Pickup collection should produce the same durable scan/operational event path, preserving the pickup's evidence reference as protected opaque metadata.
- Hub intake and sortation must not silently rewrite parcel custody. Unexpected or duplicate scans become explicit discrepancies.
- A dispatch wave cannot depart with unresolved discrepancies unless a separately authorized, reason-bearing exception is recorded.
- Container dispatch/receipt must retain the seal verification requirement from M11 and correlate the manifest reference.
- Tracking is append-only; current parcel state is a projection and can be rebuilt from accepted facts.

## Verification and rollout

The domain command invariants have unit tests. The PostgreSQL adapter still needs an implementation of `HubOperationsStore` and Testcontainers coverage proving concurrent same-key ingestion, same-key/different-payload conflict, cross-tenant isolation, transaction rollback between receipt/tracking/outbox writes, and full Flyway migration through V15. Do not enable scan intake in production until those adapter and integration tests pass.
