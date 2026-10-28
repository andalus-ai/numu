# Purchasing application

This module orchestrates use cases over purchasing-owned aggregates and the public purchasing SPI.

## Scope

- Tenant-scoped requisition creation, line addition, submission, and approval.
- Tenant-scoped purchase-order creation, line addition, approval, and dispatch.
- Goods receipt recording with stable receipt identity and replay-payload validation.
- Atomic receipt and purchase-order updates through `PurchasingUnitOfWork`.

## Adapter requirements

- Repository lookups must scope by tenant and identifier; identifiers alone are not authorization.
- Persistence adapters must enforce unique tenant-scoped requisition, purchase-order, and receipt identities.
- Receipt writes and purchase-order received-quantity transitions must participate in one transaction via `PurchasingUnitOfWork`. A multi-store deployment must use a durable workflow/outbox rather than claim a local transaction is distributed.
- Concurrent receipt requests must use database uniqueness and aggregate concurrency control (locking or optimistic versioning). The application-level replay check is not a substitute for those constraints.
- Domain events pulled from aggregates must be published through the platform's durable event/outbox mechanism by the composition adapter; this module does not claim to provide event delivery.

## Automotive parts integration

Automotive parts should call these application use cases through a composition adapter. It should preserve the work-order/part-demand reference on the requisition and map supplier SKU/offer identity without creating a second supplier or product catalog. Inventory remains responsible for stock movements; purchasing remains responsible for supplier commitments and receipt records.
