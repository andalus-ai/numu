# Agriculture Marketplace — M0 Capability Audit

Repository: `andalus-ai/numu`. Initial source-informed audit only; not a full dependency or test audit.

## Existing capabilities confirmed

| Need | Existing owner | Evidence |
|---|---|---|
| Product identity/catalog | Product | `product/pom.xml` declares domain, SPI, application, adapter and catalog. |
| Commercial availability/channel offers | Commerce Offering | `commerce/offering` has domain, SPI, application and adapter. `ProductOffering` references a product, channel and optional seller ID; it supports draft/active/suspended/retired states. |
| Inventory/stock/reservations | Supply Inventory | `InventoryService` exposes reserve, release, expire and reserved-issue flows; `InventoryReservation` is tenant-scoped with ACTIVE/COMMITTED/RELEASED/EXPIRED states. `InventoryRepository` declares atomic reservation/issue contracts. Several SPI methods have default implementations that throw `UnsupportedOperationException`, so concrete adapter support must be verified before relying on them. |
| Demand allocation and shipment handoff | Supply Fulfillment | `FulfillmentOrder` models requested lines, allocation and shipment reference; its domain comment says it does not post inventory movements or call carriers directly. |
| Procurement | Supply Purchasing | Existing purchasing bounded context is declared in `supply/purchasing/pom.xml`. |
| Shipment/courier | Logistics Courier | `Shipment` is tenant-owned and tracks booking lifecycle, sender/recipient snapshots, service level and parcels. |
| Fleet | Logistics Fleet | `logistics/numou-fleet` exists as a fleet bounded context. |
| Payment/accounting | Finance | `finance/pom.xml` includes payment, accounting, billing, tax and Syirkah reactors. |
| Optional channel coordination | Commerce Omnichannel | `commerce/omnichannel` is described as optional channel coordination, not a replacement for domain capabilities. |

## Initial decision

Do not create a second product catalog, inventory ledger, procurement system, shipment lifecycle or payment/accounting ledger for agriculture. Agriculture should add production/lot/quality/traceability semantics and connect through public contracts to existing capabilities.

A generic marketplace order owner has not been confirmed by this narrow audit. Verify existing Commerce and Fulfillment contracts before introducing any new order aggregate.

## Required next checks

1. Trace Offering seller identity, pricing, availability and order consumers.
2. Inventory already models reservations and idempotent reserved issues in the application/SPI. Verify concrete persistence-adapter overrides and integration tests: several SPI defaults intentionally throw `UnsupportedOperationException`, so declarations alone do not prove the production adapter implements the contract.
3. Trace Fulfillment-to-Shipment handoff and identify order versus fulfillment identity ownership.
4. Inspect Finance Payment and Accounting contracts, callback/replay behavior and settlement ownership.
5. Verify tenant/actor authorization, durable event delivery, migrations, tests and current CI.
6. Search for existing agriculture, marketplace, cooperative or livestock domains before adding artifacts.

## Verification status

No Maven build or tests were run. This audit used repository files via GitHub. The repository has multiple Maven reactor roots and no root `pom.xml`; use the affected reactor's documented command after the implementation boundary is known.

## M1 acceptance

- One clear owner for order lifecycle; no competing order aggregate.
- Inventory reservation is authoritative under concurrent requests, and the selected adapter actually implements atomic reservation and idempotent reserved-issue contracts.
- Payment status derives from verified payment capability state.
- Cross-capability actions are idempotent, tenant-scoped and auditable.
- Agriculture lot references preserve traceability without owning generic stock, payment or shipment state.
