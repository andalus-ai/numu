# Supply Chain M9 — Transactional warehouse persistence and outbox dispatch

## Delivered
- PostgreSQL JDBC implementation of the existing warehouse repository port.
- One transaction commits the warehouse aggregate snapshot, command receipt (including the exact result snapshot), and outbox events.
- Tenant-scoped aggregate lookups and command-replay reads.
- Optimistic aggregate-version updates and idempotency fingerprint conflict detection.
- Durable outbox with bounded multi-worker claims using PostgreSQL `FOR UPDATE SKIP LOCKED`, expiring leases, delivery attempt counts, and publish/retry operations.
- Schema constraints and indexes for tenant identity, version integrity, pending-event scans, and lease expiry.

## Reliability semantics
- A successful command's state, receipt, and emitted event rows commit atomically.
- A repeated tenant/command key with the same fingerprint returns the recorded command result; a different fingerprint is rejected.
- Concurrent updates that lose the expected-version race are reported as retryable conflicts.
- Outbox publication is at-least-once: consumers must be idempotent. The claim lease can expire after a worker crash.
- The adapter targets PostgreSQL because it uses `TIMESTAMPTZ`, partial indexes, `ON`-database row locks and `UPDATE .. RETURNING` semantics.

## Validation still required
This milestone adds implementation and migration, but database-backed integration tests and a full Maven build must run in CI or a configured PostgreSQL environment before production deployment. The current contract tests do not substitute for PostgreSQL concurrency/crash tests. Migration discovery must be configured by the consuming deployment; the module does not start Flyway automatically.
