# Automotive Parts → Purchasing integration

This adapter translates explicit workshop external-parts demand into a Purchasing-owned requisition. It does not own supplier catalogs, purchase orders, goods receipts, inventory, or stock movements.

- The caller supplies a stable demand ID, tenant and purchasing context, work-order references, canonical part reference, fitment snapshot reference, quantity/UOM, and estimated unit price.
- The demand ID is reused as the tenant-scoped requisition ID. A retry with the same payload recovers the same requisition without appending a duplicate line.
- Reusing a demand ID with different header or line data is rejected.
- New requisitions are submitted for the normal Purchasing approval workflow; this adapter does not approve them or select a supplier.
- UOM and work-order/fitment provenance are carried in the line description because the current RequisitionLine contract does not model typed UOM or source references. This is a documented limitation, not a substitute for a future versioned line contract.

## Goods receipt → Inventory posting

`GoodsReceiptInventoryPostingService` is the explicit anti-corruption boundary for accepted purchasing receipts. It resolves the purchase-order line through a tenant-scoped mapping policy, applies an explicit receipt-unit-to-inventory-unit conversion, and posts a receipt movement through `InventoryService` using a stable command key scoped by tenant and receipt ID.

- Purchasing remains authoritative for the receipt and accepted quantity; Inventory remains authoritative for movements and stock balances.
- The mapping policy must deterministically resolve the same tenant/PO-line to stock item, warehouse, conversion factor, unit cost, and currency for a receipt's lifetime.
- The conversion factor is inventory base units per receipt unit; unit cost is expressed per inventory base unit.
- Replaying the same receipt uses the same idempotency key. Reusing that key with a changed quantity is rejected by the Inventory adapter contract.
- The mapping must be validated against the tenant's catalog and receiving authorization before posting.

**Production boundary:** use the PostgreSQL Inventory adapter (which commits movement and command receipt atomically) together with `JdbcReceiptPostingLedger` for durable multi-instance retries. Apply the integration migration `db/migration/V1000__receipt_posting_ledger.sql` with the deployment's normal migration runner. The ledger stores a tenant-scoped payload fingerprint, PENDING/POSTED state, attempt count, movement ID, and last failure. A crash after Inventory commits but before the ledger is marked POSTED is recovered by retrying the same receipt command key; Inventory returns the original movement rather than posting stock twice.

The ledger and Inventory are still separate transactions, not a distributed transaction. The pending-row index supports reconciliation workers, but this change does not add a scheduler, dead-letter policy, or operator-facing reconciliation endpoint. The two-argument service constructor remains for tests/single-process use; production wiring should inject the JDBC ledger and durable Inventory adapter.


## Durable adapter verification

`PostgresGoodsReceiptInventoryPostingTest` exercises receipt replay against the PostgreSQL Inventory adapter. It recreates the application service/repository between deliveries to verify the database command receipt—not an in-memory cache—prevents duplicate movements, and checks that a changed receipt payload is rejected. The test uses Testcontainers and is skipped when Docker is unavailable.

This verifies Inventory-side durable idempotency for this integration path. It does not yet make the Purchasing receipt update and Inventory movement one distributed transaction; operational recovery still depends on retrying the same stable receipt identity and reconciling the durable Inventory command receipt.
