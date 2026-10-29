# POS Platform M0: Repository Audit and Boundary Decisions

**Status:** Initial repository audit; implementation readiness not yet established  
**Target capability:** `commerce/pos`  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)  
**Baseline reviewed:** `main` at `df79f65bd2abfccefd67ca2ac1e55856f4a525d0` (2026-10-29)

## Purpose

Record evidence from the current repository before introducing a neutral POS core. This is a bounded initial audit, not a claim that all adjacent capability contracts or builds have been fully verified.

## Observed current structure

- `commerce/pos/pom.xml` aggregates `numou-pos-domain`, `numou-pos-application`, and `numou-pos-adapter`.
- POS domain/application packages currently use `tech.kayys.numou.groceries`; the public `PosService` API is grocery-shaped.
- `PosService` exposes product registration, batch lots, shelf-life updates, scale registration/connection/read/tare, weighted-item cart entry, transaction completion, expiry queries/marking, and waste processing.
- Grocery-specific identifiers include `ProductId`, `ScaleId`, and `ShelfItemId`. The domain POM has JUnit and ArchUnit test dependencies and an explicit boundary comment keeping this context's product model distinct from the canonical catalog Product aggregate.
- `commerce/omnichannel` contains core, payload-store, and PostgreSQL modules. PR #152 is merged and introduced provider-neutral channel contracts; omnichannel should remain optional for POS.
- `commerce/pricing` has domain, application, SPI, and adapter modules; `commerce/promotion` has domain, application, and adapter modules plus documentation.
- The guessed paths `commerce/catalog` and `commerce/inventory` do not exist. The repository does have `product/product-catalog`, `commerce/offering`, and `supply/inventory`; these are likely relevant owners, but their public contracts still need inspection.
- Payment is organized under `finance/payment`; accounting and tax are under `finance/accounting` and `finance/tax`. Organization has domain/application/SPI/adapter modules; tenancy has domain/application/SPI/adapter modules; CRM is present. Exact branch/store, party/customer, authorization, and payment integration contracts remain to be checked.
- `AGENTS.md` and ADR-003 require neutral domain behavior, explicit public contracts, compatibility-preserving change, multiple deployment compositions, and no assumption that a capability must be a microservice.

## Initial gap assessment

| Area | Evidence observed | Audit conclusion |
|---|---|---|
| Sale lifecycle | Existing `completeTransaction` API; full invariants are not established by the public interface alone | Inspect implementation, persistence, and tests before defining neutral sale states |
| Register and shifts | No register/shift operations appear in the inspected `PosService` interface | Likely gap; search all source and operational capabilities before adding models |
| Tenders and payment outcomes | No tender orchestration appears in the inspected `PosService` interface | Inspect `finance/payment` public contracts; POS must not own provider payment truth |
| Catalog and sellable offerings | `product/product-catalog` and `commerce/offering` exist | Inspect both APIs and choose the canonical contract; do not create a duplicate catalog |
| Inventory | `supply/inventory` exists | Inspect reservation/commit/reversal and stock-movement contracts; no direct cross-capability table writes |
| Pricing and promotions | Separate module trees exist | Reuse public application/SPI contracts; inspect exact request/response contracts before integration |
| Finance, customer/party, organization/branch, authorization | Relevant finance, CRM, organization and tenancy module trees exist | Public-contract and ownership audit remains pending |
| Omnichannel | Existing modules and merged channel-contract milestone verified | Compose optionally; do not make POS depend on inbound webhook infrastructure or duplicate channel identity |
| Grocery vertical | Scale, weighted item, lot, expiry, and waste API is visible | Preserve as an opt-in extension or compatibility façade; do not force these concepts into all retail POS deployments |
| Deployment topology | POS has a separate adapter; ADR-003 establishes target architecture | Keep domain independent of HTTP/CDI/persistence/provider SDKs; topology belongs in composition/adapters |

## Boundary decisions

1. **Extend the existing POS capability.** Do not create a parallel POS product or second transaction source of truth.
2. **Introduce neutral behavior incrementally.** Avoid a package-wide rename or breaking the existing grocery API in the first implementation milestone.
3. **Keep sellable-item identity distinct from catalog ownership.** POS may hold sale-time references and immutable sale snapshots; the catalog remains authoritative for product/variant data.
4. **Keep price/promotion decisions owned by their capabilities.** POS stores the applied result and policy/version references needed to explain a completed sale; it must not become a second pricing engine.
5. **Keep payment execution and stock/accounting mutations outside the POS domain.** POS coordinates through public contracts and records outcomes; payment owns provider execution, inventory owns stock movements, and finance owns accounting.
6. **Use explicit scope and command metadata.** Tenant, branch/store, register, operator, correlation, and idempotency requirements belong at the application boundary. Identifiers alone do not grant authorization.
7. **Make verticals opt-in.** Grocery scales/expiry/waste, cafe modifiers/kitchen workflow, fashion variants/exchanges, and wholesale terms belong in extension contracts where their invariants differ.
8. **Model offline behavior honestly.** Local pending state, server acceptance, conflict, and recovery are distinct outcomes. Do not promise distributed exactly-once execution; design idempotent processing and reconciliation.
9. **Do not claim tests pass from repository inspection.** Maven and GitHub Actions were not run in this audit.

## M1 entry criteria

Before adding neutral core types:

- Inspect the full current POS domain/application/adapter sources, persistence mappings, and POS tests.
- Inspect public contracts for product catalog/offering, inventory, payment, accounting/tax, CRM/party, tenancy, organization/branch, and authorization.
- Inspect pricing and promotion public APIs, not their module names alone.
- Check current CI and the narrow POS Maven reactor; record baseline failures separately from any new change.
- Define the smallest compatible neutral contract around register/shift/sale/line/tender/receipt only after checking for canonical equivalents.
- Add domain invariant tests and architecture guards in the same milestone PR as implementation.

## Verification status

- `AGENTS.md` and ADR-003 reviewed.
- POS aggregator, domain POM, and public `PosService` inspected.
- Omnichannel directory and merged PR #152 status verified.
- Pricing, promotion, offering, product catalog, inventory, payment, accounting/tax, organization, tenancy and CRM module locations identified.
- Full adjacent-capability public-contract audit: **pending**.
- Maven compilation/tests and GitHub Actions: **not run in this audit**.
- No production-readiness claim is made.
