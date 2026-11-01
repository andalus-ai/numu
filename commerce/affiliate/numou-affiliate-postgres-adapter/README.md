# Affiliate PostgreSQL persistence

This module owns durable storage for payout intents, settlement batches, audit events, and integration outbox records. It depends on Affiliate domain/SPI contracts and does not own transfer execution; Wallet/Payment remain responsible for money movement.

## Implemented

- PostgreSQL schema for payout intents, settlement batches/items, payout reservations, audit events, and the event outbox.
- PostgresPartnerPayoutRepository implements the payout repository SPI with tenant-scoped reads, database-enforced idempotency, immutable-intent conflict detection, and compare-and-set lifecycle updates.
- PostgresSettlementBatchRepository persists batches and their item snapshots; batch creation locks payout rows in stable order, verifies tenant/currency/partner/amount/status, and inserts reservations in the same transaction. The database primary key on tenant+payout remains the concurrency authority.
- Settlement batch updates use expected-version compare-and-set, reject immutable-intent changes and illegal lifecycle transitions, and release reservations transactionally when a batch is cancelled or completed.
- Repository methods return CompletionStage to match the application SPI. JDBC work currently executes synchronously on the calling thread; callers must not treat this as non-blocking I/O.

## Outbox operations

- Delivery claims remain tenant-scoped and bounded, using PostgreSQL FOR UPDATE SKIP LOCKED, expired-lease recovery, and worker/lease compare-and-set acknowledgement.
- Dead-letter inspection is tenant-scoped and keyset-paginated by (occurred_at, outbox_id), with a maximum page size of 100.
- Operational summaries omit raw payloads and provider error strings. The operator-facing list contains event and aggregate identifiers, type, attempts, occurrence time, and version only.
- The global health snapshot reports pending, due, in-flight, expired-lease, dead-letter, and oldest-due-age values. It intentionally contains no tenant identifiers and must be exposed only through a host-protected internal operations/metrics surface.
- The existing redrive operation moves only a tenant-scoped DEAD_LETTER event back to PENDING and appends an actor-attributed audit event in the same transaction.

## Transaction and delivery contract

The payout update uses a compare-and-set predicate over tenant, payout id, current status, and current update timestamp. The database version is incremented on each successful update. Idempotency uniqueness is enforced by PostgreSQL, not by an in-memory pre-check.

Audit and outbox records are appended in the same JDBC transaction as the relevant payout lifecycle updates and settlement-batch creation/transitions. Outbox delivery is at-least-once; consumers must be idempotent, and expired leases must be reclaimable.

## Remaining before production

- A managed worker lifecycle, concrete versioned Event-platform publisher adapter, and Micrometer export/alert integration are still separate deployment milestones.
- The host must authenticate and authorize tenant operators before constructing AffiliateCommandContext; the host must protect global health data behind internal operations authorization.
- PostgreSQL integration tests cover migration behavior, tenant-scoped claims, lease expiry/reclaim, stale worker rejection, dead-letter pagination, and aggregate health calculations.
- Concurrent multi-node reservation stress tests, end-to-end consumer delivery, and verification against the supported CI reactor remain required.
- JDBC remains synchronous behind the CompletionStage SPI. Do not treat it as non-blocking I/O.
