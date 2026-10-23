# Supply Chain Milestone 5 — Warehouse execution foundation

## Scope
Adds neutral warehouse execution for receiving, put-away, picking, and packing, suitable for retail, wholesale, manufacturing, e-commerce, service parts, and construction logistics.

## Included
- New warehouse Maven aggregator with domain and application modules.
- Explicit OPEN, IN_PROGRESS, COMPLETED, and CANCELLED lifecycle.
- Quantity validation, duplicate-line rejection, over-execution prevention, and completion only when every planned quantity is executed.
- Location/package context for put-away, picking, and packing.
- Tenant-scoped repository contract, optimistic version checks, idempotency receipts, and versioned outbox events.
- SHA-256 command fingerprints; conflicting idempotency-key reuse is rejected.
- Inventory remains authoritative for stock balances and movements.

## Production gates
Persistent adapter, migrations, outbox publisher, task assignment/worker model, and inventory movement integration are not included. A production adapter must enforce unique (tenant_id, command_key), tenant isolation, optimistic writes, and atomic aggregate + receipt + outbox commits. Maven and integration tests have not been run in this environment; CI is not claimed green.
