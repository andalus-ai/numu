# Supply Chain Milestone 3 — Tenant-scoped application boundary

## Goal

Make tenant scope explicit at the inventory application boundary before extending warehouse and fulfillment workflows. The implementation supports tenant-aware routing without tying the domain to a specific deployment model.

## Implementation

- Added `TenantInventoryRepositoryProvider`, the explicit port for resolving an inventory repository bound to one tenant.
- Added `TenantScopedInventoryService`; every public inventory operation requires a tenant identifier and resolves a tenant-bound repository.
- Applied tenant scoping to idempotency namespaces, preventing the same caller key from colliding across tenants in shared command-receipt storage.
- Added application tests for tenant routing, idempotency scoping, and rejecting blank tenant IDs.
- Kept the existing `InventoryService` API for compatibility with single-tenant and legacy integrations.

## Security and persistence contract

This facade does **not** turn an unscoped repository into a tenant-safe repository. A provider must never return a shared unscoped repository. The persistence adapter must enforce tenant predicates on every inventory item, warehouse, movement, reservation, and idempotency read/write. In shared-table databases, tenant ID must participate in relevant unique constraints and query predicates; schema/database-per-tenant deployments must validate tenant selection against authenticated identity.

Tenant IDs supplied to this service must come from trusted authentication/authorization context, not directly from untrusted request payloads. Authorization (which user may access which tenant and operation) remains a separate policy.

For distributed deployments, tenant routing must be deterministic and consistent across replicas. In-memory tenant maps are not a production isolation strategy. Database implementations must preserve atomic issue/transfer/reservation guarantees across concurrent requests and processes.

## Idempotency requirements

- Scope command receipts by tenant and caller-provided command scope/key.
- Persist the original canonical request fingerprint and outcome in the same transaction as the ledger movement(s).
- Same key + same fingerprint returns the original outcome.
- Same key + different fingerprint fails deterministically without writing additional movements.
- Use unique constraints and transactional locking/conditional writes; process-local synchronization is insufficient.
- Retain receipts at least as long as the caller retry horizon and business audit requirements dictate.

## Next implementation gate

The next cohesive milestone should add tenant-aware persistent adapter coverage and reusable warehouse execution/fulfillment contracts (receiving, put-away, picking, packing, and shipment handoff). Those workflows must model explicit state transitions and idempotent command boundaries; they should not be represented as UI-only status flags.

## Verification status

Tests are added but were not executed in this environment. Do not treat this milestone as CI-verified or production-ready until the full Maven reactor and relevant integration tests pass against a tenant-scoped persistent adapter.
