# Supply Chain & Logistics — Milestone 1 Baseline

**Status:** architecture/domain baseline; not a production-readiness certification.  
**Repository baseline:** `main` at the time this milestone branch was created.  
**Scope:** inventory, purchasing, and fleet/logistics. No vertical-specific assumptions are introduced.

## Goals

1. Establish bounded-context ownership before adding more features.
2. Record verified gaps that could cause stock, procurement, tenant-isolation, or shipment inconsistencies.
3. Add low-risk domain validation at the inventory-ledger boundary.
4. Define measurable gates for Milestones 2–6.

## Current structure observed

- `supply/inventory` already separates domain, application, SPI, and adapter modules.
- `supply/purchasing/numou-purchasing-domain` contains purchase orders, purchase-order lines, requisitions, receipts, vendor contracts, and domain events.
- `logistics/numou-fleet` contains fleet domain models and repository contracts; its POM currently declares `numou-foundation`.
- Inventory item identity currently includes SKU, name, valuation/tracking methods, and a free-form UOM string.
- Inventory movements are immutable records and stock is projected from movement history.
- Inventory reservations include a tenant identifier and optimistic version field. The inspected inventory item, warehouse, and movement records do not currently carry tenant identity.
- Purchase-order lines use free-form item codes and do not share a typed product reference with inventory items.

These observations describe the inspected source paths; they do not imply that every adapter or deployment path has been audited.

## Ownership boundaries (target)

| Capability | Authoritative owner | Must not own |
|---|---|---|
| Product identity and classification | Catalog/product capability | Stock balances, purchase-order state, vehicle state |
| Inventory ledger, reservations, stock availability | Inventory | Supplier contracts, carrier assignment |
| Supplier, requisition, purchase order, receipt workflow | Purchasing/procurement | Physical stock ledger posting implementation |
| Warehouse work and handling units | Warehouse (planned capability) | Independent duplicate stock truth |
| Shipment, carrier allocation, delivery milestones | Transportation (planned neutral capability) | Fleet ownership or inventory balances |
| Vehicles, drivers, fuel, maintenance | Fleet | General shipment lifecycle |
| Forecasts, replenishment proposals, optimization | Planning (planned capability) | Directly mutating authoritative stock without a command/approval path |
| Tenant membership, authorization, audit policy | Shared platform capabilities | Per-module ad hoc security rules |

Construction, manufacturing, retail, e-commerce, and software-delivery workflows should compose these neutral capabilities; they must not become dependencies of the supply-chain core.

## Findings and priority

| ID | Priority | Finding | Required response |
|---|---|---|---|
| SC-01 | P0 | `InventoryService.issue` reads availability and then saves a movement; the service contract shown here does not make that sequence atomic. | M2: add a repository/transaction command that reserves or issues conditionally under concurrency; add a two-orders/one-unit race test. |
| SC-02 | P0 | `InventoryService.transfer` persists outbound and inbound movements as separate calls. A failure between them can leave a partial transfer. | M2: make transfer one atomic operation or a durable transfer aggregate with compensating/recovery semantics. |
| SC-03 | P0 | Inventory movements/items/warehouses do not expose tenant identity in the inspected domain records, while reservations do. | M2: define tenant-scoped repository contracts and storage constraints before enabling multi-tenant writes; migrate compatibility deliberately. |
| SC-04 | P0 | UOM is a free-form string on inventory items and reservations; purchase-order quantities are not linked to a governed unit/conversion contract. | M2: adopt shared typed quantity/UOM contracts and explicit conversions; never compare incompatible units directly. |
| SC-05 | P0 | Purchase-order line receipt updates add the supplied quantity without validating positivity or the remaining open quantity in the inspected domain method. | M2: validate receipt deltas and define over-receipt tolerance/approval policy; test duplicate and replayed receipts. |
| SC-06 | P0 | The inspected `InventoryService` has no idempotency key for external receipt/issue commands. | M2: add command identity and durable deduplication at the persistence boundary. |
| SC-07 | P1 | Product references are duplicated as inventory `ItemId`/SKU and purchasing `itemCode`. | M2: establish a canonical product reference contract while retaining external supplier/customer item codes as mappings. |
| SC-08 | P1 | Fleet is present, but no neutral shipment/consignment lifecycle is evident in the inspected fleet domain paths. | M4: add transportation independently from fleet so third-party carriers are first-class. |
| SC-09 | P1 | Inventory balance calculation currently scans movements in the application service. | M2: retain the ledger as truth, move projection/replay strategy behind repository/read-model contracts, and benchmark realistic volume. |
| SC-10 | P1 | The logistics README claims broad enterprise features, but the claims are not verified by this source inspection. | Keep documentation claims tied to implemented code and automated tests. |

## M1 implementation in this branch

- Reject negative inventory unit costs and blank/malformed currency codes at the immutable movement boundary.
- Normalize currency codes to uppercase using a locale-independent rule.
- Add focused regression tests for those invalid ledger entries.
- Keep the larger behavioral changes above out of this baseline branch; they require persistence/transaction contract changes and migration design rather than isolated service edits.

## Required test and architecture gates

### Domain correctness
- Movement quantity is non-zero; unit cost is non-negative; currency code is exactly three ASCII letters after normalization.
- Reservations are positive, versioned, and transition only from ACTIVE.
- PO receipt deltas cannot be negative, duplicated, or exceed the configured tolerance once M2 policy is implemented.
- Quantities are compared only after explicit UOM conversion.

### Persistence and concurrency (M2 gate)
- Two concurrent issues competing for the final unit cannot both succeed.
- A transfer is all-or-nothing from the inventory perspective.
- Replaying a receipt/issue command does not create a second ledger movement.
- Tenant A cannot read or mutate Tenant B's stock, reservations, warehouses, or movement history.
- Ledger records are append-only; corrections use reversal/adjustment movements rather than edits/deletes.

### Architecture and deployment
- Domain modules do not depend on REST, database, messaging, or a specific runtime.
- Application code depends on domain and outbound ports, not concrete adapters.
- Inventory does not depend on purchasing or fleet; purchasing and transportation integrate through typed commands/events.
- A single-process deployment and remote-service adapters implement the same contracts.
- Integration events are versioned; durable outbox/inbox, retries, dead-letter handling, audit, and observability are required before distributed rollout.

## Exit criteria

M1 is complete when the domain boundary validation and tests are merged, the ownership/gap baseline is reviewed, and the repository's CI provides a green module test/build result. M1 does **not** claim multi-tenant inventory, concurrency-safe stock, atomic transfer, or production-ready warehouse/transport execution. Those are explicit M2/M3/M4 deliverables.
