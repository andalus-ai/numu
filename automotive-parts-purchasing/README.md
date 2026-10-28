# Automotive Parts → Purchasing integration

This adapter translates explicit workshop external-parts demand into a Purchasing-owned requisition. It does not own supplier catalogs, purchase orders, goods receipts, inventory, or stock movements.

- The caller supplies a stable demand ID, tenant and purchasing context, work-order references, canonical part reference, fitment snapshot reference, quantity/UOM, and estimated unit price.
- The demand ID is reused as the tenant-scoped requisition ID. A retry with the same payload recovers the same requisition without appending a duplicate line.
- Reusing a demand ID with different header or line data is rejected.
- New requisitions are submitted for the normal Purchasing approval workflow; this adapter does not approve them or select a supplier.
- UOM and work-order/fitment provenance are carried in the line description because the current RequisitionLine contract does not model typed UOM or source references. This is a documented limitation, not a substitute for a future versioned line contract.

Production requirements: the repository adapter must enforce tenant-scoped requisition-ID uniqueness and serialize concurrent modifications. The current application layer does not provide a cross-process idempotency lock or transactional outbox. Goods receipt must later post stock through Inventory's own command contract.
