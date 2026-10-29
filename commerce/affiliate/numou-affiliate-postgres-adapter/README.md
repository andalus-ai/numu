# Affiliate PostgreSQL persistence

This module owns the durable schema for partner payout intents and settlement batches. It depends on Affiliate domain/SPI contracts and does not own transfer execution; Wallet/Payment remain responsible for money movement.

## Schema guarantees

- Tenant-scoped uniqueness for payout and batch idempotency keys.
- Composite tenant foreign keys for batch items.
- Positive amounts, valid lifecycle values, timestamp ordering, and provider references for paid payouts.
- Version columns intended for optimistic concurrency.
- A payout-to-batch uniqueness guard intended to prevent concurrent assignment to multiple active batches.

The migration is the storage foundation only. Repository implementations, atomic payout reservation with batch creation, transactional audit events, and PostgreSQL integration/concurrency tests must land before enabling this adapter in production. Migration validation is required against the supported PostgreSQL version; partial-index predicate limitations must be checked in CI and resolved before merge.
