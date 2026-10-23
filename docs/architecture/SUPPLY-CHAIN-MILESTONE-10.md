# Supply Chain M10 — Recovery-aware inventory/warehouse execution

## Delivered
- Added `RecoverableWarehouseInventoryExecutionCoordinator`, which uses the durable execution journal around inventory-first receiving and reserved picking.
- Binds a tenant-scoped command key to a stable SHA-256 fingerprint of the operation payload.
- Reuses the same inventory and warehouse command key on retries, relying on each owning subsystem's idempotency boundary.
- Records inventory completion before committing warehouse execution; records warehouse completion after the warehouse command returns.
- Resumes PREPARED and INVENTORY_POSTED attempts, retries NEEDS_RECONCILIATION attempts, and returns the current work order for already committed journal entries.
- Keeps stock balance authority in inventory and physical execution state in warehouse.

## Recovery cases
- Crash before inventory posting: PREPARED can be retried.
- Inventory succeeds but journal transition fails: replay uses the same inventory key and should return the original inventory result.
- Inventory journaled but warehouse command not committed: retry resumes from INVENTORY_POSTED.
- Warehouse command commits but process crashes before the journal is updated: warehouse command replay uses the same key and returns the persisted receipt.
- Different payload reusing the same tenant/command key is rejected by the journal fingerprint check.

## Important limits
- The guarantee depends on the inventory gateway and warehouse repository actually enforcing durable idempotency for the same tenant and command key.
- This coordinator is not a distributed transaction. Reconciliation is still required if an owning system loses its idempotency receipt or returns an ambiguous result.
- This milestone does not add a scheduled recovery worker, distributed journal leases, or a production PostgreSQL test harness.
- Full Maven/CI and PostgreSQL integration verification must pass before production rollout.
