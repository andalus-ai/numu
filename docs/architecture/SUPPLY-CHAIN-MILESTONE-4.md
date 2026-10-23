# Supply Chain Milestone 4 — Fulfillment command and outbox boundary

## Scope

This milestone adds the application boundary needed to run neutral fulfillment across a standalone application, modular monolith, or distributed services. It does not assume a particular database or message broker.

## Included

- `numou-fulfillment-application` module and a tenant-scoped `FulfillmentApplicationService`.
- Stable command keys and canonical SHA-256 request fingerprints, including canonical decimal quantities.
- Repository contract for idempotent command receipts, optimistic aggregate-version checks, and atomic persistence of aggregate state + command receipt + outbox events.
- Typed outcomes and exceptions for optimistic-concurrency conflicts and reuse of an idempotency key with a different payload.
- Versioned integration event names for request, state change, and shipment handoff request.
- Explicit two-step shipment lifecycle: `STAGED -> HANDOFF_REQUESTED -> SHIPPED`. Carrier/warehouse handoff must be confirmed before the order is marked shipped.
- Allocation confirmation is intentionally separate from reservation execution: call it only after the inventory workflow has successfully reserved the allocated quantity.

## Persistence contract (mandatory)

A production repository must:
1. Scope every order and command receipt by trusted tenant identity.
2. Enforce unique `(tenant_id, command_key)` in the database.
3. In one local database transaction, compare expected aggregate version, persist the new aggregate, store the command fingerprint and original result snapshot, and append outbox rows.
4. Return the stored result for same-key/same-fingerprint retries; reject same-key/different-fingerprint retries.
5. Reject stale expected versions rather than silently overwriting newer state.
6. Publish outbox events asynchronously with at-least-once delivery; consumers must deduplicate by event ID and validate tenant metadata.

The interface alone does not supply these guarantees; every adapter must prove them with database integration tests. The in-memory reference adapter has not been added in this milestone because it would not validate cross-process transaction semantics.

## Inventory and fulfillment coordination

Inventory remains authoritative for reservations and stock ledger movements. A workflow adapter should consume `fulfillment.requested.v1`, reserve inventory using tenant-scoped idempotent inventory commands, and then call `confirmAllocation` with a stable command key only after reservation succeeds. Failed allocation must release any reservation created by that workflow, using a durable saga/compensation record when inventory and fulfillment do not share a transaction.

Warehouse execution owns pick/pack task execution. The application service accepts confirmed quantities, while the aggregate enforces `packed <= picked <= allocated <= requested`. Shipment handoff requests are emitted from the outbox; the carrier adapter must later confirm handoff before `confirmShipped` is called.

## Production gates

Still required before production:
- persistent adapter and migrations for aggregate, command receipt, and outbox tables;
- transactional integration tests for races, retries, stale versions, tenant isolation, and crash recovery;
- inventory workflow/saga implementation and reservation expiry/release compensation;
- warehouse task entities and adapter implementations;
- outbox publisher/inbox deduplication, observability, retention, and operational replay tools.

Tests were added/updated but not executed in this environment. No CI success is claimed.
