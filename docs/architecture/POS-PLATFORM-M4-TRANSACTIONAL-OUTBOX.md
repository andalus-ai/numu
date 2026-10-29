# POS Platform M4: Transactional outbox foundation

**Status:** Draft implementation for review; not production-verified.  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)

## Goal

Persist integration-event envelopes in the same database transaction as the business mutation. This prevents the classic dual-write failure where a sale commits but its event is lost, or an event is published for a sale that later rolls back.

## Ownership and boundary

- POS application owns the outbox event envelope and writer port.
- The PostgreSQL adapter owns JSONB storage and delivery-state columns.
- Shared foundation continues to own `Money` and `Currency`.
- Payment, inventory, pricing, tax, and accounting remain separate capabilities.
- This milestone does not publish events, call external systems, or claim exactly-once delivery.

## Included

- `PosOutboxEvent` immutable envelope with tenant, aggregate identity, event type, schema version, occurrence time, and JSON payload.
- `PosOutboxWriter` application port.
- PostgreSQL `pos_outbox_event` schema with delivery state, attempt count, retry scheduling fields, published timestamp, and indexes.
- PostgreSQL writer using the active JTA transaction.
- Test coverage for persisted event metadata and for sale + outbox + idempotency rollback as one command fails.

## Transaction contract

The producer must append the event inside the same transaction as the owning business mutation. The writer deliberately does not commit independently. A failed command should roll back both its business write and its outbox row.

A dispatcher is not included yet. A future dispatcher must claim rows safely under concurrency, commit its claim before external I/O, use bounded retries/backoff, record failures, and assume at-least-once delivery. Consumers must deduplicate by stable `event_id`; this is not exactly-once delivery.

## Current limitations

- Existing sale lifecycle commands are not yet wired to emit concrete domain/integration events through this port.
- No dispatcher, broker publisher, dead-letter operations UI, retention policy, or event replay endpoint is included.
- PostgreSQL integration tests and the full Maven reactor have not been executed in this session. Transaction enlistment must be confirmed by running the Docker-backed tests.
- Outbox payloads must not contain payment secrets, full card data, or unnecessary personal data.

## Acceptance criteria

1. The POS Maven reactor compiles.
2. PostgreSQL tests prove a sale, idempotency result, and outbox row commit or roll back together.
3. Duplicate event IDs are rejected without silently overwriting an existing event.
4. The next milestone wires sale lifecycle transitions to explicit versioned event contracts before any dispatcher is introduced.
