# Agriculture & Breeder Marketplace — Implementation Roadmap

**Status:** Implementation plan grounded in the M0 source audit; not a statement that capabilities are implemented.
**Repository:** `andalus-ai/numu`
**Baseline:** M0 capability audit merged in PR #794.

## Product objective

Deliver a reusable marketplace capability for farmers, livestock breeders, cooperatives/aggregators, business buyers, consumers, and optional third-party transport providers. Indonesia is the first deployment market; jurisdiction, commodity, and transaction-date rules must be configurable for later regions.

Marketplace behavior must be reusable outside agriculture. Agriculture adds production, lot, quality, and traceability evidence; it must not fork product catalog, inventory, payment, procurement, fulfillment, shipment, or fleet behavior.

## Confirmed existing capability boundaries

- **Product + Commerce Offering:** product identity and channel-specific commercial availability. Offering has an optional seller ID, but this alone is not a complete seller onboarding, authorization, or marketplace contract.
- **Inventory:** reservation/issue contracts exist. The inspected implementation is in-memory only and several SPI methods default to `UnsupportedOperationException`. Production order acceptance must not depend on those defaults.
- **Payment:** `PaymentApplicationPort` is a framework-neutral consumer contract. The PostgreSQL adapter atomically records payment obligations, idempotency fingerprints, and outbox events; verified outcomes are tenant-scoped and transactionally processed, with provider event inbox uniqueness. This is a payment obligation, not a marketplace order aggregate.
- **Fulfillment:** owns demand allocation and shipment handoff, not inventory movements or carrier execution.
- **Courier/Shipment + Fleet:** existing logistics owners; marketplace should integrate through public contracts.
- **Purchasing:** existing procurement capability for B2B replenishment/supplier workflows where its semantics fit.
- **No generic marketplace order owner was found** in the inspected repository tree. Restaurant ordering is a separate domain and should not be reused as the universal marketplace order model.

## Milestones

### M0 — Capability audit (completed; PR #794 merged)

- Record current owners, reusable contracts, and the inventory durability gap.
- Avoid duplicate generic ledgers and lifecycle owners.

### M1 — Marketplace commercial and order boundary

**Goal:** introduce one industry-neutral owner for marketplace order lifecycle without making it a duplicate of payment, inventory, fulfillment, or shipment.

Scope:
- Seller participation/profile and seller authorization contracts; do not treat a seller UUID on an offering as authorization.
- Listing/offer reference to Product + Offering, seller identity, sales channel, and validity.
- Order and immutable line snapshots: product/offer reference, seller, quantity/unit, unit price, currency, taxes/fees/discount allocation when applicable, delivery promise, and buyer/seller party snapshots.
- Explicit order state machine and allowed transitions; separate payment, allocation, fulfillment, shipment, cancellation, and refund state references.
- Tenant-scoped command idempotency with request fingerprint conflict detection.
- Application ports for listing/offer eligibility, inventory reservation, payment obligation creation, and fulfillment request; no adapter-private table access.
- Outbox/inbox or equivalent durable orchestration for retries and recovery. Never claim a distributed transaction across separate capabilities.
- Unit tests for lifecycle invariants, duplicate commands, mismatched idempotency payloads, partial failure and retry semantics.

Not in scope: production database adapter until transaction and migration patterns are established; provider-specific payments; carrier-specific booking; agriculture lot schemas; crowdfunding.

**Acceptance:**
1. One marketplace order owner; Payment remains owner of payment status and refund execution.
2. Order acceptance cannot be reported successful unless the chosen inventory adapter implements durable atomic reservations.
3. A retried command returns the same logical result; reusing a key for a different payload is rejected.
4. Tenant and actor authorization are enforced at application boundaries.
5. Snapshot prices and terms do not silently change when offers are later edited.
6. Failure recovery is explicit and observable; no cross-service ACID claims.

### M2 — Durable inventory reservation

- Verify whether an existing durable Inventory adapter exists before creating one.
- If absent, add a separately selectable PostgreSQL adapter under Inventory's own boundary, following existing Quarkus/Flyway/Testcontainers conventions.
- Enforce tenant-aware conditional reservation against on-hand less active reservations, atomically with idempotency record.
- Atomically commit a reserved issue and reservation state transition; make release, expiry, replay, and conflicting key semantics explicit.
- Test concurrent oversell prevention, process restart, duplicate/conflicting commands, tenant isolation, and transaction rollback with PostgreSQL/Testcontainers.
- Keep the in-memory adapter as a reference/test adapter, clearly not production durable storage.

### M3 — Marketplace seller and sourcing workflows

- B2C direct purchase and B2B RFQ/quote/contracted supply.
- Supplier eligibility, onboarding state, service areas, catalog/offer publishing, price and availability validity.
- Cooperative/aggregator pooling with explicit source seller/lot attribution and allocation policy.
- Buyer purchase orders and seller acceptance where appropriate; reuse Purchasing only where the existing purchasing contract matches the workflow.

### M4 — Agriculture extension

- Farm/producer and facility references; crop/livestock production cycles.
- Harvest/production lots with origin, estimated/actual quantity, availability window, grade/specification, and evidence.
- Lot allocation to order lines and immutable chain-of-custody references.
- Quality inspection, grading, certificates, recall/withdrawal, and livestock identity/health/movement evidence where relevant.
- Keep regulatory requirements versioned/configurable by jurisdiction, commodity, and effective date. Verify Indonesian rules with authoritative sources before asserting compliance.

### M5 — Optional crowd and group commerce

- Group-buy campaign, minimum/target quantity, close time, contribution/reservation state, threshold outcome, cancellation/refund orchestration.
- Pooled supply and shared transport capacity are opt-in extensions; ordinary marketplace orders work without them.
- Define who owns funds and cancellation obligations; integrate Finance Payment/Refund contracts rather than keeping balances in marketplace.
- Protect against duplicate joins, threshold races, oversubscription, late payments, and campaign-close/retry races.

### M6 — Transport provider marketplace

- Request transport quotes/capacity from eligible fleet providers based on pickup/dropoff, commodity constraints, time window, and service area.
- Keep route distance/ETA provider-owned; geographic straight-line distance is not a road estimate.
- Create shipment/transport work only through public logistics contracts, preserving shipment custody and provider assignment history.
- Support direct seller delivery, buyer-arranged transport, and third-party transport without forcing every listing into a logistics flow.

### M7 — Production readiness and regionalization

- Threat model, tenant isolation tests, actor permissions, audit, retention and privacy policy.
- Durable outbox/inbox, reconciliation, retry/dead-letter handling, metrics and operational dashboards.
- Database migrations, backup/restore, deployment profiles, API/event compatibility and reproducible builds.
- Indonesia-first tax, commodity, permit, transport and consumer/business policies behind effective-dated jurisdiction rules; legal/compliance review required before production claims.
- Standalone/library, modular monolith, and distributed deployment composition tests where supported.

## Cross-cutting invariants

- No duplicate inventory, payment, shipment, or fleet ledger.
- Every cross-capability command includes tenant context and stable idempotency semantics.
- Stable IDs and published contracts cross boundaries; never share private persistence entities.
- Commercial snapshots are immutable after acceptance; corrections use explicit amendments.
- Events are delivered at least once and consumers are idempotent; do not promise exactly-once delivery.
- Optional crowd commerce and transport integrations do not become mandatory transitive dependencies for every consumer.
- Domain code remains infrastructure-neutral; persistence and partner protocols live in adapters.
- Tests and CI are required evidence. Documentation and interface declarations do not prove runtime behavior.

## Immediate next implementation

Implement M1's neutral marketplace order domain and application contract after inspecting the project's current POM/reactor and testing conventions. Keep the first commit focused on domain invariants and contract tests; do not expose an order-acceptance endpoint or claim production readiness until M2's durable reservation contract is implemented and verified.
