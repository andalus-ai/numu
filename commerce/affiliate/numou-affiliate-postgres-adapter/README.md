# Affiliate PostgreSQL persistence

This module owns durable storage for payout intents, settlement batches, audit events, and integration outbox records. It depends on Affiliate domain/SPI contracts and does not own transfer execution; Wallet/Payment remain responsible for money movement.

## Implemented

- PostgreSQL schema for payout intents, settlement batches/items, payout reservations, audit events, and the event outbox.
- `PostgresPartnerPayoutRepository` implements the payout repository SPI with tenant-scoped reads, database-enforced idempotency, immutable-intent conflict detection, and compare-and-set lifecycle updates.
- `PostgresSettlementBatchRepository` persists batches and their item snapshots; batch creation locks payout rows in stable order, verifies tenant/currency/partner/amount/status, and inserts reservations in the same transaction. The database primary key on tenant+payout remains the concurrency authority.
- Settlement batch updates use expected-version compare-and-set, reject immutable-intent changes and illegal lifecycle transitions, and release reservations transactionally when a batch is cancelled or completed.
- Repository methods return `CompletionStage` to match the application SPI. JDBC work currently executes synchronously on the calling thread; callers must not treat this as non-blocking I/O.

## Transaction and delivery contract

The payout update uses a compare-and-set predicate over tenant, payout id, current status, and current update timestamp. The database version is incremented on each successful update. Idempotency uniqueness is enforced by PostgreSQL, not by an in-memory pre-check.

Audit and outbox tables are foundations only at this stage. The complete implementation must write business state, audit records, and outbox records in the same transaction. Outbox delivery is at-least-once; consumers must be idempotent, and expired leases must be reclaimable.

## Remaining before production

- Audit and outbox records are now appended in the same JDBC transaction as payout lifecycle updates and settlement-batch creation/transitions. Batch creation uses its maker as actor; payout lifecycle updates use the explicit `SYSTEM` service actor because the current payout repository SPI has no authenticated actor context.
- `PostgresAffiliateOutboxRepository` claims tenant-scoped events with `FOR UPDATE SKIP LOCKED`, reclaims expired leases, increments attempts, and uses owner/expiry compare-and-set checks for delivery and retry/dead-letter transitions.
- Delivery remains at-least-once. A worker must make its downstream operation idempotent using `eventKey`; a successful external side effect followed by a crash can cause redelivery.
- PostgreSQL integration tests for migrations, idempotent batch replay, tenant-scoped reads, rollback after a reservation conflict, and payout lifecycle regression. The Testcontainers suite skips when Docker is unavailable.
- Concurrent multi-node reservation stress tests, a full application-level authenticated actor propagation path, production dispatcher wiring/metrics, and end-to-end consumer delivery remain outstanding.
- CI verification against the supported PostgreSQL version. Do not enable this adapter for production payout processing until these are complete.
