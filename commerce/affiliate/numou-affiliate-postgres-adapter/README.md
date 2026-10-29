# Affiliate PostgreSQL persistence

This module owns durable storage for payout intents, settlement batches, audit events, and integration outbox records. It depends on Affiliate domain/SPI contracts and does not own transfer execution; Wallet/Payment remain responsible for money movement.

## Implemented

- PostgreSQL schema for payout intents, settlement batches/items, payout reservations, audit events, and the event outbox.
- `PostgresPartnerPayoutRepository` implements the payout repository SPI with tenant-scoped reads, database-enforced idempotency, immutable-intent conflict detection, and compare-and-set lifecycle updates.
- Repository methods return `CompletionStage` to match the application SPI. JDBC work currently executes synchronously on the calling thread; callers must not treat this as non-blocking I/O.

## Transaction and delivery contract

The payout update uses a compare-and-set predicate over tenant, payout id, current status, and current update timestamp. The database version is incremented on each successful update. Idempotency uniqueness is enforced by PostgreSQL, not by an in-memory pre-check.

Audit and outbox tables are foundations only at this stage. The complete implementation must write business state, audit records, and outbox records in the same transaction. Outbox delivery is at-least-once; consumers must be idempotent, and expired leases must be reclaimable.

## Remaining before production

- Settlement-batch repository and transactionally atomic payout reservation/release.
- Transactional audit and outbox repositories integrated with business mutations.
- PostgreSQL integration tests for migrations, duplicate requests, tenant isolation, stale updates, concurrent reservations, rollback, and lease recovery.
- CI verification against the supported PostgreSQL version. Do not enable this adapter for production payout processing until these are complete.
