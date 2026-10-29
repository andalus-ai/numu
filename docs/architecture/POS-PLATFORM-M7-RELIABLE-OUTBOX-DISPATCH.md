# POS Platform M7 — Reliable Outbox Dispatch

## Goal

Deliver committed POS lifecycle events to pluggable messaging infrastructure without coupling the POS domain or application layer to a broker. Delivery is at-least-once; consumers must deduplicate by the immutable `eventId`.

## Design

- `PosOutboxDeliveryStore` is the persistence port for claims and delivery outcomes.
- `PosEventPublisher` is the provider-neutral publishing port.
- `PosOutboxDispatcher.dispatchOnce()` performs one bounded batch and is deliberately independent of a scheduler. A deployment may invoke it from a managed scheduler or dedicated worker.
- PostgreSQL claims use `FOR UPDATE SKIP LOCKED`, bounded batch sizes, a lease expiry, and a per-claim `lease_token`.
- The dispatcher publishes outside the claim transaction. A successful publish is acknowledged in a separate transaction.
- Lease tokens fence stale workers: after a lease expires and another worker reclaims the row, the old worker cannot mark or reschedule it.
- Failures return to `PENDING` with capped exponential backoff. Once the configured attempt limit is reached, the row becomes `DEAD` and retains its last error.

## Operational semantics

1. Claim due rows and increment `attempt_count`.
2. Publish each event. Use `eventId` as the broker message identifier/key when supported.
3. Mark published only after the publisher returns successfully.
4. On failure, schedule a retry or dead-letter the row according to `maxAttempts`.
5. Monitor pending age, in-flight lease expiry, attempt counts, and dead-letter volume. Dead rows require an operator-controlled replay/recovery action.

A process crash after broker acknowledgement but before database acknowledgement can produce a duplicate publish. This is expected under at-least-once semantics. Consumers should maintain an inbox/deduplication record keyed by event ID in the same transaction as their business effect.

## Configuration

The dispatcher requires positive lease duration, initial/max backoff, batch size, and max attempts. The caller owns scheduling frequency and worker lifecycle. Production deployments should tune lease duration above expected publish latency and ensure publisher timeouts are shorter than the lease.

## Migration

V3 adds lease fencing columns and indexes. Existing legacy `IN_FLIGHT` rows are reset to `PENDING` for safe recovery.

## Verification status

Unit tests cover success, retry backoff, and dead-letter threshold. PostgreSQL concurrency/lease-expiry tests, scheduler wiring, metrics, broker adapter, and end-to-end delivery tests remain follow-up work. Do not treat this milestone as a complete production deployment until those are verified.
