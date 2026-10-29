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


## Provider-token encryption (N5 security gate)

The JDBC adapter now writes provider tokens only as AES-GCM ciphertext, with a key identifier stored separately. A host supplies `PushTokenKeyProvider`; production composition must resolve active and historical keys from a managed KMS/secret manager. Never put key bytes in source control, the database, application configuration checked into the repository, or logs. The protector binds ciphertext to tenant and recipient IDs using authenticated additional data, so moving ciphertext between owners fails authentication. Device identity is intentionally not part of the AAD because an idempotent token refresh can retain an existing device ID while the registration command carries a new candidate ID. Rotation is supported by retaining old key IDs for decryption and writing new registrations with the active key; a future bounded re-encryption job should rotate stored envelopes.

Migration V4 preserves pre-existing plaintext rows as legacy rows so deployment does not silently destroy device registrations. The new repository fails closed when it encounters such a row; it will not pass legacy plaintext into a provider. Before enabling delivery on an upgraded installation, either require affected users to re-register devices or run a separately reviewed, audited offline re-encryption migration using the actual key-management integration, then verify that no legacy rows remain. Do not add a plaintext fallback to the worker. The deployment must also restrict database access and backups and define key rotation/recovery procedures.


## N6 — bounded legacy token migration

The JDBC adapter provides `LegacyPushTokenMigrator` as an explicit maintenance operation. It is not started automatically by application startup or by the delivery worker.

```java
LegacyPushTokenMigrator migrator =
    new LegacyPushTokenMigrator(dataSource, tokenProtector, clock);
LegacyPushTokenMigrator.BatchResult result = migrator.migrateBatch(100);
if (result.complete()) {
    // Perform the independent zero-legacy-row verification gate.
}
```

Supply a `PushTokenProtector` whose key provider is backed by the deployment's managed KMS/secret manager. Never use a test key or a key embedded in source/configuration for production data.

- Each call processes at most 1,000 legacy rows in one database transaction. Use a smaller batch to bound row locks and transaction duration.
- `FOR UPDATE SKIP LOCKED` allows multiple migration workers to process disjoint rows. Re-running is safe: encrypted rows are not selected again.
- On any row-protection or database failure, the current batch is rolled back. The exception is intentionally generic and does not expose a token, ciphertext, or database value.
- Migration V5 creates a restricted operational audit table containing batch ID, timestamps, scanned/migrated/remaining counts, outcome, and a bounded failure code. It never stores token material.
- Repeat batches until `BatchResult.complete()` is true. Then independently query for legacy or malformed token-storage rows and verify the count is zero before enabling external delivery.
- The migrator does not provision keys, verify KMS recovery, or enable provider delivery. Those remain host deployment responsibilities. Back up the database securely and test key recovery before migration; loss of the encryption key makes migrated tokens unusable.
- If migration repeatedly fails, stop and investigate using batch IDs and sanitized diagnostics in a separately authorized maintenance procedure. Do not add plaintext fallback behavior to the repository or worker.
