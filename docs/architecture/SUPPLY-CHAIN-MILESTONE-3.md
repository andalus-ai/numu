# Supply Chain Milestone 3 — Tenant scope and fulfillment domain

## Goal

Introduce an explicit tenant-scoped inventory application boundary and the first neutral fulfillment domain model. This milestone lays the foundation for warehouse execution without making inventory depend on sales, construction, or carrier-specific modules.

## Tenant-scoped inventory

- Added `TenantInventoryRepositoryProvider` to resolve a repository bound to one tenant.
- Added `TenantScopedInventoryService`; every public inventory operation requires a tenant identifier and resolves a tenant-bound repository.
- Applied tenant scoping to idempotency namespaces, preventing caller keys from colliding across tenants in shared command-receipt storage.
- Added tests for tenant routing, idempotency scoping, and rejecting blank tenant IDs.
- Kept the existing `InventoryService` API for compatibility with single-tenant and legacy integrations.

This facade does **not** turn an unscoped repository into a tenant-safe repository. A provider must never return a shared unscoped repository. Persistence adapters must enforce tenant predicates on every item, warehouse, movement, reservation, and idempotency read/write. In shared-table databases, tenant ID must participate in relevant unique constraints and query predicates; schema/database-per-tenant deployments must validate tenant selection against authenticated identity.

Tenant IDs must come from trusted authentication/authorization context, not directly from request payloads. Authorization remains a separate policy. Persistent adapters must preserve atomic issue/transfer/reservation guarantees across concurrent requests and processes.

## Neutral fulfillment domain

- Added a dedicated `numou-fulfillment-domain` module under the supply aggregator.
- Added tenant-scoped fulfillment orders and stable IDs.
- Added line quantity invariants: packed <= picked <= allocated <= requested.
- Added explicit allocation, pick, pack, stage, ship, deliver, cancel, and reject transitions.
- Picking may proceed for allocated quantities even when the rest of an order is backordered.
- Shipment handoff requires a shipment reference; fulfillment does not write inventory movements or call carriers directly.
- Added regression tests for lifecycle order, quantity limits, duplicate line IDs, and cancellation after shipment.

The fulfillment domain remains independent of commerce, construction, and transport implementations. A future application layer must coordinate inventory reservation/issue commands and persist fulfillment changes transactionally or via an outbox-backed workflow.

## Idempotency requirements

- Scope command receipts by tenant and caller-provided command scope/key.
- Persist the original canonical request fingerprint and outcome in the same transaction as ledger movement(s).
- Same key + same fingerprint returns the original outcome.
- Same key + different fingerprint fails deterministically without writing additional movements.
- Use unique constraints and transactional locking/conditional writes; process-local synchronization is insufficient.
- Retain receipts for the caller retry horizon and business audit requirements.

## Production and verification gates

The current reference inventory adapter remains in-memory. Production rollout requires a persistent adapter with tenant-scoped queries/constraints, durable command fingerprints, atomic issue/transfer/reservation operations, authorization integration, reconciliation tooling, observability, and concurrent multi-tenant integration tests.

Tests have been added but were not executed in this environment. Do not treat this milestone as CI-verified or production-ready until the full Maven reactor and integration tests pass against a tenant-scoped persistent adapter.
