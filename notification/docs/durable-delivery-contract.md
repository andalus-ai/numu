# Notification N4 — durable delivery contract

## Ownership and guarantees

The notification capability owns provider-delivery attempts. The inbox remains the canonical user-visible notification record; a delivery job is an operational attempt to deliver that notification to one registered device. Creating a job does not imply that a provider accepted it, and provider success must not change inbox read state.

The queue is database-backed so process restarts and horizontally scaled workers do not lose work. NotificationDeliveryJobRepository is the application port. Implementations must enforce idempotent enqueue, atomic claims, and lease-token fencing for every completion/retry/dead-letter transition.

## Application orchestration

- NotificationDeliveryEnqueuer creates one durable job for each active device with a deterministic idempotency key. Repeated enqueue calls do not create duplicate jobs.
- NotificationDeliveryWorker claims a bounded batch and processes it synchronously when invoked by a host scheduler. It creates no background threads and is not automatically started by the capability.
- NotificationDeliveryProvider is a provider-neutral port. A host must explicitly compose an adapter before provider calls can occur.
- NotificationDeliveryRetryPolicy applies capped exponential delay with bounded jitter and a maximum attempt count. Only normalized, non-sensitive error codes are persisted.

The worker resolves the notification and active device under the job tenant/recipient scope immediately before sending. If the notification or active device no longer exists, the job is dead-lettered. Provider success means only that the provider accepted the request, not that the device displayed it.

## Security and data handling

- Never log provider tokens, authorization headers, message bodies, or provider response bodies.
- DeliveryRequest.toString() redacts device token and message content.
- last_error_code is for a bounded, non-sensitive normalized code only; do not store raw exception messages.
- Provider token encryption at rest and key rotation remain a release gate before external push delivery is enabled.
- Provider adapters must have bounded network timeouts. Retry policy must cap attempts and use bounded exponential backoff with jitter.
- Provider failure must not remove or mutate the canonical inbox item.

## Migration and compatibility

Migration V3 adds a queue table referencing existing inbox and device records. This slice adds application orchestration only; it does not start a worker, send pushes, or change inbox APIs. Existing inbox/device rows remain valid.

## Acceptance criteria

1. PostgreSQL adapter enqueues idempotently and claims rows atomically under concurrent workers.
2. Lease-token fencing prevents stale completion after lease expiry/reclaim.
3. Retry/dead-letter transitions are tenant-scoped and bounded.
4. Testcontainers tests cover duplicate enqueue, two-worker claim contention, expired lease recovery, stale-token rejection, and tenant isolation.
5. Application orchestration provides bounded processing, idempotent fan-out, redacted request representation, and retry-policy tests.
6. Provider-token encryption and key rotation, metrics, and operational replay remain release gates before production push delivery is enabled.
