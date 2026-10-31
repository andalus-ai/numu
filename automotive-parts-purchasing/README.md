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

Migration V1003 adds an append-only requeue audit table. A PostgreSQL trigger writes an audit event in the same transaction as every successful `DEAD_LETTER → PENDING` transition, so the operational state change and its audit evidence cannot diverge. The database rejects updates and deletes to audit rows. `JdbcReceiptPostingRequeueAuditRepository` exposes tenant-and-receipt-scoped history for operator tooling; it is read-only and enforces a bounded result size.

The host application owns worker scheduling, authentication/authorization implementation, operator endpoints, alerting, and operational retention. Do not expose the ledger adapter directly as an unauthenticated HTTP endpoint.

## Verification

PostgreSQL Testcontainers tests cover durable Inventory idempotency, tenant isolation, stale claims, lease recovery, repeated requeues, and immutable audit history. Deployment-level authorization and operator audit integration tests belong in the host application.
