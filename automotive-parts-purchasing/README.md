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

**Production limitation:** the in-memory Inventory adapter only provides single-process idempotency. This integration is not production-ready for multi-instance deployments until a durable Inventory adapter enforces command-key uniqueness and commits the movement and idempotency record atomically. Receipt persistence and inventory posting also cross module transaction boundaries; a durable outbox/inbox or recoverable posting state is still required to reconcile a receipt accepted by Purchasing when Inventory is unavailable. This service does not claim a distributed transaction or exactly-once delivery.
