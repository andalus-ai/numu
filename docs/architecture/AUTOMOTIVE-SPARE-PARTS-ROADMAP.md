# Automotive Spare Parts Capability Roadmap

- **Status:** Proposed implementation roadmap; M0 repository reconnaissance started
- **Repository:** `andalus-ai/numu`
- **Audience:** Vehicle-service, supply-chain, commerce, and platform maintainers
- **Last reviewed:** 2026-10-28

## 1. Objective

Deliver a production-oriented spare-parts capability for independent mechanics, multi-branch workshops, distributors, retailers, fleets, and marketplace participants. Start with Indonesian workshop operations and preserve the ability to compose the same business rules into standalone applications, a modular monolith, or selected distributed services.

This document is a plan and an initial source-tree audit. It does not claim that the roadmap's capabilities are complete, production-ready, or passing CI.

## 2. Initial repository findings

The repository already contains reusable building blocks that should be evaluated before any new implementation:

- `vehicle-service/` contains a vehicle-service domain and application reactor. Its domain includes `WorkshopWorkOrder`; its application includes appointment booking and service-offering contracts.
- `supply/inventory/` contains inventory domain, application, SPI, and adapter artifacts. The domain includes inventory items, movements, stock balances, warehouses, tracking/valuation methods, storage locations, and reservations. The application exposes inventory operations, and existing tests cover idempotent movements/reserved issues and reservation availability.
- `product/product-catalog/` and the product capability contain catalog/listing and canonical product concepts. The catalog must not become a second owner of stock or seller-specific offers.
- `supply/purchasing/` contains purchasing domain models for requisitions, purchase orders, goods receipt, and vendor contracts.
- `finance/accounting/` includes procurement-related matching code, which may be useful for later supplier invoice reconciliation.

The initial tree scan did **not** establish that all required production adapters, cross-capability contracts, vehicle-fitment semantics, seller offers, or marketplace order lifecycle already exist. Verify those contracts and their tests before choosing reuse or adding new code. In particular, an in-memory inventory adapter is present; its existence alone is not evidence that production persistence is absent or complete.

Repository-wide constraints from `AGENTS.md` and ADR-003 apply: capabilities are business boundaries, not automatically services; inspect reactor roots; keep domain code infrastructure-neutral; use public contracts rather than private persistence models; and do not claim tests or CI passed without evidence.

## 3. Ownership and boundaries

| Capability | Owns | Must not become |
| --- | --- | --- |
| Vehicle service | Workshop work order, diagnosis, repair jobs, labor, mechanic assignment, service lifecycle | The owner of shared stock, canonical product identity, supplier procurement, or marketplace settlement |
| Automotive parts | Part identity and automotive semantics: OEM/aftermarket references, fitment, supersession, approved substitutes, kits, and fitment evidence | A duplicate generic product catalog or inventory ledger |
| Product/catalog | Canonical product identity and generic catalog concepts, where existing contracts fit | The authority for workshop stock or seller-specific offers |
| Inventory | Warehouses/locations, stock ledger, reservations, movements, tracking, valuation, and reconciliation | Automotive fitment rules or marketplace order state |
| Purchasing | Requisitions, purchase orders, receiving, supplier terms, and purchasing lifecycle | Workshop repair lifecycle or stock-ledger implementation |
| Marketplace/commerce | Seller participation, offers, price/availability snapshots, customer orders, commission and dispute lifecycle where not already implemented | The owner of physical stock belonging to a seller, unless an explicit inventory ownership model says so |
| Finance | Accounting, receivables/payables, tax/invoice policy, settlement | A duplicate source of operational stock quantities |
| Logistics/fulfillment | Pick/pack/dispatch, shipment, tracking, delivery and return transport | Seller offer pricing or repair-job state |

Use the existing module/package conventions after examining their contracts. The proposed logical name “automotive parts” does not require creating a new Maven artifact if an existing capability can own the behavior without violating its boundary.

## 4. Core domain rules

1. A part identity is not a stock balance. A catalog part can exist without being stocked at any location.
2. A seller offer is not a part identity. Offers carry seller-specific price, condition, availability promise, minimum order, lead time, fulfillment policy, and validity.
3. Fitment must be evidence-aware. Do not infer compatibility from a free-text vehicle label alone; support vehicle attributes, OEM references, engine/variant constraints where applicable, and explicit fitment confidence/source.
4. A substitute is not automatically equivalent. Store the substitution relationship and policy/approval needed for the repair context.
5. Reservation is a commitment, not a physical movement. Issue, consumption, return, and adjustment are separately auditable transitions.
6. Commands that affect inventory or orders must have stable idempotency semantics. A retry must not deduct stock, create a receipt, or charge/settle twice.
7. Availability exposed to commerce must account for the seller's committed stock and policy; marketplace listings must not silently oversell inventory.
8. Every stock issue, return, and adjustment must identify tenant, location, item, quantity/UOM, actor, time, reason, source reference, and correlation/idempotency key as appropriate.
9. Workshop cancellation releases eligible reservations. Closing a job reconciles reserved, issued, installed/consumed, unused, damaged, and returned quantities.
10. Cross-capability integration uses documented public application ports in-process or versioned API/event contracts across deployment boundaries. Use durable delivery/outbox patterns where required; do not couple modules through shared private tables.

## 5. Mechanic-first workflow

1. Create or open a workshop work order and record diagnosis.
2. Search for a part using description, SKU/barcode, OEM/aftermarket reference, or vehicle fitment.
3. Show compatible parts and explicitly marked substitutes, with provenance and any uncertainty.
4. Check availability by workshop branch/location and reserve stock.
5. If stock is unavailable, request supplier quotes, create/continue a purchase requisition or PO, or find a seller offer.
6. Receive the part through the purchasing/inventory workflow and preserve receipt, lot/serial, and supplier references when applicable.
7. Issue parts against the work order; record actual installed/consumed quantities, mechanic, unused items, damage, and returns.
8. Close the repair only after the configured parts reconciliation policy is satisfied.
9. Hand off actual parts/labor costs to existing finance/billing contracts without duplicating accounting logic.

## 6. Milestones and acceptance criteria

### M0 — Repository audit and boundary decision

- Read root `AGENTS.md`, ADR-003, the capability packaging contract, and the capability ownership matrix.
- Inspect actual public APIs, POM dependencies/reactor roots, migrations/adapters, tests, and CI for vehicle service, inventory, product/catalog, purchasing, commerce, finance, and logistics.
- Identify existing marketplace/order/seller-offer contracts and whether inventory persistence is production-capable.
- Record dependency direction, integration gaps, current verification status, and a smallest coherent implementation plan.
- **Exit:** no duplicate capability is introduced before reuse evidence and the owner of each invariant are documented.

### M1 — Part identity and fitment

- Reuse canonical product identity where its contract fits; add automotive-specific references and fitment as an extension/bounded context.
- Support tenant-scoped part identifiers, OEM and aftermarket references, supplier SKUs, barcode aliases, UOM, condition, and lifecycle.
- Model vehicle fitment and explicit substitute/supersession relationships with source/evidence and effective dates where needed.
- Add deterministic domain tests for invalid/ambiguous fitment, duplicate references, tenant isolation, and substitute policy.
- **Exit:** a mechanic can resolve a part request to compatible candidates without requiring an inventory record or seller offer.

### M2 — Inventory contract integration

- Reuse existing inventory APIs and ledger; do not add a second stock balance model.
- Verify transaction/concurrency behavior, tenant scoping, reservations, idempotent issue/receipt, return and adjustment, valuation precision, and production adapter/migration status.
- Add or evolve only the public contracts required for parts/work-order references and reasoned issue/return.
- **Exit:** concurrent reservations/issues cannot oversell stock, retries do not duplicate movement, and every movement is traceable to its source.

### M3 — Workshop parts operations

- Add a work-order parts request/line lifecycle using public vehicle-service and inventory contracts.
- Support reserve, issue, consumption/install, unused return, damaged/defective return, cancellation release, and close-out reconciliation.
- Preserve actor, technician, work-order, location, item, quantity, UOM, cost snapshot, and idempotency/correlation references.
- Add an end-to-end test for reserve → issue → partial consumption → unused return → close, including retry and cancellation cases.
- **Exit:** mechanic operations reconcile work-order quantities and inventory ledger with no duplicate stock movement.

### M4 — Supplier sourcing and purchasing integration

- Reuse existing purchasing requisitions, POs, goods receipt, supplier/vendor contracts, and invoice-matching capabilities where contracts fit.
- Add automotive-specific mapping only at the boundary; support partial receipts, backorders, supplier SKU mapping, and discrepancy handling.
- **Exit:** a missing part can be sourced and received with complete PO/receipt/stock traceability.

### M5 — Marketplace offers and fulfillment

- Audit and reuse existing commerce/order/payment/fulfillment capabilities before implementing missing seller workflows.
- Keep seller offers distinct from canonical parts and stock; define seller-owned, workshop-owned, consigned, and marketplace-facilitated stock explicitly.
- Support fitment-aware discovery, offer snapshots, stock/lead-time promises, quote/B2B terms, order lifecycle, cancellation/refund, disputes, and logistics integration as required by existing ownership.
- **Exit:** orders cannot oversell committed stock, and seller, buyer, payment, fulfillment, return, and dispute records are traceable.

### M6 — Production and Indonesia-first readiness

- Verify tenant isolation, authorization, migrations, auditability, observability, retry/recovery, backup/reconciliation, performance, and deployment composition.
- Support IDR and configurable Indonesian tax/invoice policy through existing finance/tax contracts; do not hardcode rates or imply legal compliance without validation.
- Validate multi-branch workshop/distributor use cases, local SKU/barcode conventions, provider adapters, and regional extension points.
- Treat Sharia-compliant trade as an explicit optional policy/profile with transparent price/terms, ownership and delivery evidence, and qualified review; a generic marketplace flow is not automatically Sharia-compliant.
- **Exit:** documented test/CI evidence and operational runbooks exist for supported deployment profiles.

## 7. Required verification

- Unit tests for domain invariants and lifecycle transitions.
- Contract tests at vehicle-service ↔ inventory and purchasing ↔ inventory boundaries.
- Persistence integration tests for transactional stock constraints, idempotency, and concurrent commands.
- Architecture tests for dependency direction and prevention of private persistence-model imports.
- Tenant isolation tests across catalog, reservations, offers, orders, and stock.
- End-to-end workshop flow with partial consumption, returns, cancellation, and retries.
- Build only the affected Maven reactor first, then dependent reactors and repository CI where practical. Record commands and actual outcomes; do not infer success from source inspection.

## 8. Explicit non-goals for the first delivery

- Splitting every capability into a separate microservice.
- Duplicating product, inventory, purchasing, finance, payment, or logistics logic.
- Treating a marketplace listing as proof of physical stock ownership.
- Treating a vehicle model label as sufficient fitment evidence.
- Adding a universal automotive enum to neutral platform modules.
- Claiming Indonesian legal or Sharia compliance without policy validation and appropriate review.
