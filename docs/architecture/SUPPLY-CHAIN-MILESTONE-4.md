# Supply Chain Milestone 4 — Fulfillment commands, allocation saga, and outbox boundary

## Scope

This milestone adds the application boundary needed to run neutral fulfillment across a standalone application, modular monolith, or distributed services. It does not assume a particular database or message broker.

## Included

- `numou-fulfillment-application` module and a tenant-scoped `FulfillmentApplicationService`.
- Stable command keys and canonical SHA-256 request fingerprints, including canonical decimal quantities and length-prefixed fields.
- Repository contract for idempotent command receipts, optimistic aggregate-version checks, and atomic persistence of aggregate state + command receipt + outbox events.
- Typed outcomes and exceptions for optimistic-concurrency conflicts and reuse of an idempotency key with a different payload.
- Versioned integration events. The request event contains the requested lines so an allocation workflow has enough information to reserve stock.
- Explicit two-step shipment lifecycle: `STAGED -> HANDOFF_REQUESTED -> SHIPPED`. Carrier/warehouse handoff must be confirmed before the order is marked shipped.
- Inventory reservation gateway and durable allocation-saga contracts. The coordinator reserves every line before confirming allocations, uses stable hashed idempotency keys, records each reservation, and compensates prior reservations if a later line cannot be reserved.
- Recovery behavior: saga state records the original expiry; reservation lookup by command key resolves a timeout after a reservation commit; partial allocation confirmation is replayed with stable command keys instead of blindly reserving again.
- Test-only in-memory repository/gateway models and tests for retry behavior, conflicting idempotency keys, tenant scope, successful allocation, and compensation.

## Persistence contract (mandatory)

A production repository must:
1. Scope every order, command receipt, saga, reservation lookup, and outbox row by trusted tenant identity.
2. Enforce unique `(tenant_id, command_key)` and `(tenant_id, workflow_key)` in the database.
3. In one local transaction, compare expected aggregate version, persist the new aggregate, store the command fingerprint and original result snapshot, and append outbox rows.
4. Return the stored result for same-key/same-fingerprint retries; reject same-key/different-fingerprint retries.
5. Reject stale expected versions rather than silently overwriting newer state.
6. Publish outbox events asynchronously with at-least-once delivery; consumers must deduplicate by event ID and validate tenant metadata.
7. Persist saga transitions with optimistic version checks and enforce one active allocation saga per order. A new attempt requires a new workflow key after compensation.

The interfaces alone do not supply these guarantees; every adapter must prove them with database integration tests. The test-only in-memory repositories do not validate cross-process transaction semantics.

## Inventory and fulfillment coordination

Inventory remains authoritative for reservations and stock ledger movements. A workflow adapter consumes `fulfillment.requested.v1`, resolves a warehouse policy, and starts allocation. Each reservation command must be idempotent in the inventory service. If an allocation attempt fails before allocation confirmation, compensation releases reservations; if confirmation has begun, the saga retries each line using stable command keys and must not compensate already-confirmed allocations automatically.

Warehouse execution owns pick/pack task execution. The application service accepts confirmed quantities, while the aggregate enforces `packed <= picked <= allocated <= requested`. Shipment handoff requests are emitted from the outbox; the carrier adapter later confirms handoff before `confirmShipped` is called.

## Production gates

Still required before production:
- persistent adapters and migrations for aggregate, command receipt, outbox, and saga tables;
- transactional integration tests for races, retries, stale versions, tenant isolation, and crash recovery;
- concrete inventory gateway adapter plus reservation expiry/release compensation and reconciliation workers;
- warehouse task entities and adapter implementations;
- outbox publisher/inbox deduplication, observability, retention, and operational replay tools.

Tests were added but not executed in this environment. No CI success is claimed.
