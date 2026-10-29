# Affiliate PostgreSQL persistence

This module owns the durable schema for partner payout intents, settlement batches, audit events, and integration outbox records. It depends on Affiliate domain/SPI contracts and does not own transfer execution; Wallet/Payment remain responsible for money movement.

## Schema guarantees

- Tenant-scoped uniqueness for payout, batch, and outbox idempotency keys.
- Composite tenant foreign keys for batch items and payout reservations.
- Positive amounts, valid lifecycle values, timestamp ordering, and provider references for paid payouts.
- Version columns intended for optimistic concurrency.
- A payout-to-batch reservation primary key that prevents the same tenant payout being reserved by concurrent batches.
- Append-oriented audit records indexed by tenant and aggregate timeline.
- A durable event outbox with claim indexes, lease fields, retry availability, delivery state, and at-least-once semantics.

## Transaction and delivery contract

The future repository/orchestration implementation must write business state, audit records, and outbox records in the same database transaction. Outbox consumers must be idempotent: the outbox provides at-least-once delivery, not exactly-once delivery. IN_FLIGHT rows must be reclaimable after lease_until expires, and optimistic updates must include the stored version in their predicates.

The schema alone does not make the adapter production-ready. JDBC repository implementations, atomic batch creation/reservation/cancellation, transactional audit/outbox writes, and PostgreSQL integration/concurrency tests remain required before enabling this adapter in production. Validate migrations against the supported PostgreSQL version in CI.
