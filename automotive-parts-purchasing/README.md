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

`ReceiptPostingOperations` is the transport-neutral synchronous facade for non-reactive hosts. Reactive Quarkus hosts use `IdentityReceiptPostingOperations`:
- Effective permissions are loaded through Identity's `EffectivePermissionPort` in the verified tenant scope.
- Required permission codes are `automotive-parts.receipt-posting.inspect` and `automotive-parts.receipt-posting.requeue`.
- JDBC queries and ledger mutations are scheduled on Mutiny's worker pool; the event-loop thread is never blocked.
- The actor comes from Quarkus `SecurityIdentity`; the tenant comes only from a signed tenant-bound access JWT's canonical `tenant_id` claim (or the temporary compatibility alias when canonical is absent). Conflicting claims are rejected.
- The HTTP request filter accepts only authenticated `USER` access tokens (`token_use=access`) and binds their verified tenant to request-scoped `RequestContextHolder`. It ignores caller-supplied tenant headers, query parameters and body fields.
- Permission lookup failures deny access. The facade does not treat an Identity outage as an authorization bypass.
- The V1003 audit trigger persists requeue evidence in the same transaction as the ledger state transition.

### HTTP contract

The Quarkus REST adapter exposes:
- `GET /api/v1/automotive-parts/receipt-postings` — tenant-scoped operational summary.
- `GET /api/v1/automotive-parts/receipt-postings/dead-letters?cursor=&limit=50` — bounded keyset page (limit 1–200).
- `GET /api/v1/automotive-parts/receipt-postings/dead-letters/{receiptId}?historyLimit=100` — failure detail and immutable requeue history.
- `POST /api/v1/automotive-parts/receipt-postings/dead-letters/{receiptId}/requeue` — JSON body `{"reason":"..." }`; reason is mandatory and limited to 2,000 characters.

The adapter returns 401 for anonymous requests, 403 for missing/invalid tenant-bound credentials or denied permissions, 404 for a receipt outside the tenant's dead-letter scope, 400 for invalid input, and a generic 500 response for unexpected failures. It never accepts tenant or actor IDs from the HTTP request.

`ReceiptPostingOperationsProducer` composes JDBC persistence and Identity permission ports from host-provided `DataSource` and CDI beans. The host must include this adapter, Quarkus REST/Jackson, Quarkus Security, and the Identity/Foundation CDI adapters, and must configure JWT verification for the Identity issuer/audience. Do not expose JDBC repositories directly as HTTP resources.

## Verification

PostgreSQL Testcontainers tests cover durable Inventory idempotency, tenant isolation, stale claims, lease recovery, repeated requeues, immutable audit history, operator pagination, summary counts, synchronous authorization denial, and reactive Identity authorization/recovery. M16b adds unit coverage for tenant-token claim validation. Full Quarkus HTTP integration tests against a deployed host and its JWT configuration remain a deployment-level verification requirement; they are not claimed by this adapter-only test suite.
