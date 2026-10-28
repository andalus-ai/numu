# Purchasing SPI

This artifact publishes the Purchasing-owned persistence boundary for requisitions,
purchase orders, and goods receipts. It depends on the Purchasing domain and does not
select a database, transport, dependency-injection framework, or deployment topology.

## Contract rules

- Every aggregate lookup requires a tenant ID; adapters must enforce the tenant predicate
  in the actual query, not filter results after a global lookup.
- The application boundary remains responsible for authentication, authorization, actor
  attribution, validation, and command idempotency.
- Saving a purchase order after receiving goods must not lose concurrent receipt updates.
  Persistence adapters must provide transactional or concurrency-safe semantics.
- A goods receipt and its purchase-order quantity/status update must be atomic, or handled
  through a durable, recoverable workflow. A plain in-memory deduplication map is not a
  production idempotency strategy.
- Inventory remains the sole owner of stock movements and balances. A receipt record here
  is procurement evidence; it must not independently increment inventory.
- Automotive Parts may reference a supplier SKU through its existing SUPPLIER_SKU
  part-reference type. This SPI does not introduce a competing supplier-item registry.

## Scope of this milestone

This is the public persistence contract only. It deliberately does not claim that a
production adapter, application service, command idempotency journal, inventory receiving
bridge, or end-to-end sourcing workflow exists. Those must be implemented and verified
before M4 can be considered complete.
