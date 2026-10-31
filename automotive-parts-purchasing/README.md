# Automotive Parts → Purchasing integration

This adapter translates workshop external-parts demand into Purchasing-owned requisitions. It does not own supplier catalogs, purchase orders, goods receipts, Inventory, or stock movements.

## Goods receipt → Inventory posting

`GoodsReceiptInventoryPostingService` resolves an accepted Purchasing receipt through a tenant-scoped mapping policy and posts the accepted quantity to Inventory. Purchasing remains authoritative for receipt identity and accepted quantity; Inventory remains authoritative for stock movements and balances.

- The mapping must deterministically resolve tenant/PO-line to stock item, warehouse, conversion factor, unit cost, and currency.
- The conversion factor is inventory base units per receipt unit; unit cost is per inventory base unit.
- The Inventory command key remains `purchasing.goods-receipt:<tenantId>` plus the receipt ID. Do not rotate it on retry or requeue.
- The PostgreSQL Inventory adapter commits the movement and command receipt atomically. Replaying the same key and payload returns the original movement; a changed payload is rejected.
- Purchasing and Inventory are separate transactions. This is an idempotent recovery boundary, not a distributed transaction.

## Durable retry and dead-letter lifecycle

Production deployments inject `JdbcReceiptPostingLedger` and `ReceiptPostingRetryPolicy`. Apply migrations V1000–V1003 using the deployment's normal migration runner.

States:
- `PENDING`: eligible for posting or worker reconciliation.
- `POSTED`: Inventory returned the canonical movement and the ledger recorded its ID.
- `DEAD_LETTER`: configured failure budget exhausted; automatic workers do not claim it.

The attempt counter records failed posting attempts. Creating or replaying a ledger record does not increment it. A retryable failure increments it atomically; the same transaction moves the row to `DEAD_LETTER` when the configured maximum is reached and records the terminal reason/time. The maximum is 1–100 and is fixed on first creation for that receipt identity.

A worker crash after Inventory commits but before the ledger records `POSTED` leaves a pending row. Lease expiry permits another worker to replay the same Inventory command key, which returns the already-committed movement. A dead-lettered row is never silently retried: an operator must use `RequeueReceiptPosting`, provide a reason, and pass the host application's tenant-scoped `ReceiptPostingRequeueAuthorizer`. Requeue atomically resets the retry budget and records actor, reason, and timestamp. It does not change the receipt fingerprint or Inventory command key.

## Requeue audit history

Migration V1003 adds an append-only requeue audit table. A PostgreSQL trigger writes an audit event in the same transaction as every successful `DEAD_LETTER → PENDING` transition, so the operational state change and its audit evidence cannot diverge. The database rejects updates and deletes to audit rows. `JdbcReceiptPostingRequeueAuditRepository` exposes tenant-and-receipt-scoped history for operator tooling.

## Operator operations API

`ReceiptPostingOperations` is the transport-neutral application facade for a host REST, GraphQL, or RPC adapter. It exposes:
- Tenant-scoped summary counts for pending, posted, and dead-letter receipts, including the oldest dead-letter timestamp.
- Keyset-paginated dead-letter inspection (bounded page size; opaque continuation cursor).
- Per-receipt failure details and immutable requeue history.
- Explicit requeue with a mandatory operator reason.

The facade accepts no tenant or actor arguments from the caller. It obtains both from `CurrentReceiptPostingOperatorPort`, which the host must implement from its authenticated request identity and verified tenant selection. `ReceiptPostingOperationsAuthorizer` is deny-by-default and must delegate to Numou Identity's effective-permission / verified-membership checks. The intended permission names are:
- `automotive-parts.receipt-posting.inspect`
- `automotive-parts.receipt-posting.requeue`

Map those permissions to Identity's `AuthorizationRequirement.permission("automotive-parts.receipt-posting", "inspect")` and `...("automotive-parts.receipt-posting", "requeue")` through the host adapter. Do not trust tenant IDs, actor IDs, or permission claims supplied in request bodies or query parameters. Do not expose JDBC repositories directly as HTTP resources. Return 404 for receipts outside the current tenant and 403 for denied operations.

The host application owns the concrete authenticated HTTP adapter, identity wiring, worker scheduling, alerting, and operational retention.

## Verification

PostgreSQL Testcontainers tests cover durable Inventory idempotency, tenant isolation, stale claims, lease recovery, repeated requeues, immutable audit history, operator pagination, summary counts, and authorization denial. Deployment-level HTTP authentication and authorization integration tests belong in the host application.
