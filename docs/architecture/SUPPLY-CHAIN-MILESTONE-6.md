# Supply Chain Milestone 6 — Inventory-connected warehouse execution

## Goal
Connect warehouse receiving and picking to the authoritative inventory ledger without letting warehouse tasks mutate stock directly.

## Included
- Warehouse application depends on inventory application/domain modules.
- `InventoryWarehouseGateway` defines the inventory-owner boundary.
- `TenantScopedInventoryWarehouseGateway` adapts to `TenantScopedInventoryService`, preserving tenant-explicit inventory commands.
- Receiving posts an idempotent inventory receipt before committing the warehouse line execution.
- Picking posts an inventory issue against an existing reservation before committing the warehouse line execution.
- Inventory command keys are stable across retries and scoped by warehouse work order, line, and operation. The caller must reuse the same command key for a retry of the same physical execution.
- `WarehouseInventoryExecutionCoordinator` enforces the work-order type for receiving/picking and coordinates the two operations.
- Failure model: if inventory posts but the warehouse commit fails, retry the exact same command; inventory's idempotent adapter must replay the original movement, then warehouse execution can be retried.

## Explicit boundaries and limitations
- Inventory currently models stock at item + warehouse level, not individual bin/location level. Put-away bin movements must not be represented as inventory transfers until the inventory model supports location/bin identities.
- Picking requires an already-created inventory reservation. This milestone does not create reservations, choose a source warehouse, or automatically allocate demand.
- The repository/outbox must persist warehouse execution, command receipt, and event atomically. Inventory movement and warehouse task cannot share a local transaction across bounded contexts; the stable idempotency key is the recovery mechanism.
- The gateway's `TenantScopedInventoryService` adapter relies on the configured tenant repository provider and inventory adapter's durable idempotency implementation. Unsupported idempotency is a hard production blocker.
- Persistent warehouse repository, schema migrations, publisher/consumer workers, and database-backed integration tests remain future work. Maven tests and CI have not been run in this environment.
