# Notification N4 — durable delivery contract

## Ownership and guarantees

The notification capability owns provider-delivery attempts. The inbox remains the canonical user-visible notification record; a delivery job is an operational attempt to deliver that notification to one registered device. Creating a job does not imply that a provider accepted it, and provider success must not change inbox read state.

The queue is database-backed so process restarts and horizontally scaled workers do not lose work. NotificationDeliveryJobRepository is the application port. Implementations must enforce idempotent enqueue, atomic claims, and lease-token fencing for every completion/retry/dead-letter transition.

## State model

- PENDING: ready for first delivery.
- IN_FLIGHT: owned by one worker until lease_until; completion requires the lease token.
- RETRY_WAIT: failed transiently and scheduled for a future attempt.
- DELIVERED: provider accepted the request; this is not proof the device displayed it.
- DEAD: terminal failure requiring operator review or explicit replay policy.

Expired leases are reclaimable. Claim implementations should use PostgreSQL row locking (FOR UPDATE SKIP LOCKED) and update rows in one transaction. Completion operations must include tenant, job ID, IN_FLIGHT state, and lease token in the update predicate. A stale worker must never overwrite a newer worker's result.

## Security and data handling

- Never log provider tokens, authorization headers, message bodies, or provider response bodies.
- last_error_code is for a bounded, non-sensitive normalized code only; do not store raw exception messages.
- Provider token encryption at rest and key rotation remain a release gate before external push delivery is enabled.
- Provider adapters must have bounded network timeouts. Retry policy must cap attempts and use bounded exponential backoff with jitter.
- Provider failure must not remove or mutate the canonical inbox item.

## Migration and compatibility

Migration V3__notification_delivery_jobs.sql adds a queue table referencing existing inbox and device records. It does not start a worker, send pushes, or change inbox APIs. Existing inbox/device rows remain valid. Deploy migration before enabling a future worker.

## Acceptance criteria for the next implementation slice

1. PostgreSQL adapter enqueues idempotently and claims rows atomically under concurrent workers.
2. Lease-token fencing prevents stale completion after lease expiry/reclaim.
3. Retry/dead-letter transitions are tenant-scoped and bounded.
4. Testcontainers tests cover duplicate enqueue, two-worker claim contention, expired lease recovery, stale-token rejection, and tenant isolation.
5. Worker metrics and operational replay are added before production push delivery is enabled.
