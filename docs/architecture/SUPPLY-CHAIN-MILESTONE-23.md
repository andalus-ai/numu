# Supply Chain Milestone 23 — Managed Warehouse Recovery Worker

## Scope

M23 adds a runtime-neutral orchestration layer over the durable recovery operations from M21/M22. It is intentionally independent of Quarkus, Spring, a message broker, or a specific scheduler so deployments can choose their runtime without making the warehouse application module depend on one.

## Delivered

- Bounded claim batches (1–500) and a caller-controlled polling interval.
- A synchronous `runOnce()` entry point plus an optional `runUntilStopped()` managed loop.
- Scheduled lease renewal while a recovery handler is running; each renewal replaces the current fenced claim.
- Handler outcomes for success, retryable failure, and non-retryable failure.
- Retry-policy integration: backoff is durably scheduled through the claim-fenced repository operation; exhausted/non-retryable work is atomically dead-lettered.
- Graceful stop checks between claimed items and best-effort release of unprocessed claims.
- Dependency-free metrics hooks for claims, renewals, lease loss, retries, outcomes, polling/processing failures, and release failures.
- Unit tests for successful release, retry scheduling, exhaustion/dead-lettering, bounds, and shutdown state.

## Handler contract and safety

The handler must be idempotent and must persist the successful business/journal transition before returning `completed()`. The worker does not pretend that a callback return alone commits inventory or warehouse state. Retry/dead-letter updates are atomic repository operations that validate the exact current claim and clear its lease.

If lease renewal fails, the worker treats the claim as lost and performs no subsequent retry/dead-letter/release mutation with the stale fencing token. A handler already running may still finish its own external work; therefore handlers must use the command key/fingerprint as idempotency controls and must not assume lease loss cancels remote side effects.

## Runtime integration

- Call `runOnce()` from the application's scheduler for container-managed scheduling.
- Or dedicate a lifecycle-managed thread to `runUntilStopped()` and invoke `close()` during shutdown.
- Bridge `RecoveryMetrics` to Micrometer, OpenTelemetry, or a local metrics system in the runtime adapter.
- Keep batch size, lease, polling interval, and retry policy as deployment configuration.

## Validation status

Tests were added but not executed in this environment. No claim is made that Maven compilation or PostgreSQL integration tests passed. Before merge, run the warehouse application unit tests and the persistence JDBC test suite against the complete migration chain, including multi-worker claims, lease fencing, retry eligibility, and dead-letter persistence.
