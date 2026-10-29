# POS Platform M0: Repository Audit and Boundary Decisions

**Status:** Initial repository audit; implementation readiness not yet established  
**Target capability:** `commerce/pos`  
**Roadmap:** [Issue #1102](https://github.com/andalus-ai/numu/issues/1102)  
**Baseline reviewed:** `main` at `df79f65bd2abfccefd67ca2ac1e55856f4a525d0` (2026-10-29)

## Purpose

Record evidence from the current repository before introducing a neutral POS core. This document is a bounded initial audit, not a claim that all adjacent capabilities or builds have been fully verified.

## Observed current structure

- `commerce/pos/pom.xml` is a Maven aggregator for `numou-pos-domain`, `numou-pos-application`, and `numou-pos-adapter`.
- The domain and application package namespace is currently `tech.kayys.numou.groceries`, so the existing POS implementation is grocery-shaped rather than a neutral retail model.
- The public application API `PosService` includes product registration, batch lots, shelf-life updates, scale registration/connection/read/tare, weighted-item cart entry, transaction completion, expiry queries/marking, and waste processing.
- Existing grocery-specific identifiers include `ProductId`, `ScaleId`, and `ShelfItemId`. The domain POM contains JUnit and ArchUnit dependencies and an explicit boundary comment about keeping the grocery POS product concept separate from the canonical catalog Product aggregate.
- `commerce/omnichannel` exists with core, payload-store, and PostgreSQL modules. Omnichannel channel contracts were merged in PR #152; its design calls for optional composition and reuse of existing source-of-truth capabilities.
- `commerce/pricing` has separate domain, application, SPI, and adapter modules.
- `commerce/promotion` has separate domain, application, and adapter modules, plus documentation.
- Repository guidance in `AGENTS.md` and ADR-003 requires domain-neutral business rules, explicit public contracts, compatibility-preserving change, multiple deployment compositions, and no assumption that a capability equals a microservice.

## Initial gap assessment

| Area | Evidence observed | Audit conclusion |
|---|---|---|
| Sale lifecycle | Existing `completeTransaction` API, but the inspected public API alone does not establish full lifecycle invariants | Inspect domain implementation, persistence, and tests before defining neutral sale states |
| Register and shifts | No register/shift operations appear in the inspected `PosService` interface | Likely core gap; search the full repository and existing operational capabilities before adding models |
| Tenders and payment outcomes | No tender orchestration appears in the inspected `PosService` interface | Contract discovery required; do not implement payment-provider truth inside POS |
| Pricing and promotions | Separate capability module trees exist | Reuse their public application/SPI contracts; inspect exact request/response contracts before integration |
| Catalog and inventory | The guessed paths `commerce/catalog` and `commerce/inventory` did not resolve | **Not evidence that these capabilities are absent.** Locate canonical modules and contracts in a follow-up audit |
| Finance, customer/party, organization/branch | Not fully inspected in this pass | Required M0 follow-up before cross-capability adapters are designed |
| Omnichannel | Existing modules and merged channel-contract milestone verified | Compose optionally; do not make POS depend on inbound webhook infrastructure or duplicate channel identity |
| Grocery vertical | Scale, weighted item, lot, expiry, and waste API is visible | Preserve as an opt-in vertical extension or compatibility façade; do not force these concepts into all retail POS deployments |
| Deployment topology | Existing POS has a separate adapter and ADR-003 establishes the target contract | Keep core domain independent of HTTP/CDI/persistence/provider SDKs; topology belongs in composition/adapters |

## Boundary decisions

1. **Extend the existing POS capability.** Do not create a parallel POS product or a second transaction source of truth.
2. **Introduce neutral behavior incrementally.** Do not perform a package-wide rename or break the existing grocery API in the first implementation milestone.
3. **Keep sellable-item identity distinct from catalog ownership.** POS may hold sale-time references and immutable sale snapshots; the catalog remains authoritative for product/variant data.
4. **Keep price/promotion decisions owned by their capabilities.** POS stores the applied result and policy/version references needed to explain a completed sale; it must not silently become a second pricing engine.
5. **Keep payment execution and stock/accounting mutations outside the POS domain.** POS coordinates through public contracts and records outcomes; payment owns provider execution, inventory owns stock movements, and finance owns accounting.
6. **Use explicit scope and command metadata.** Tenant, branch/store, register, operator, correlation, and idempotency requirements must be modeled at the application boundary. Identifiers alone do not grant authorization.
7. **Verticals are opt-in.** Grocery scales/expiry/waste, cafe modifiers/kitchen workflow, fashion variants/exchanges, and wholesale terms should be separate extension contracts where their invariants differ.
8. **Offline behavior must be honest.** Local pending state, server acceptance, conflict, and recovery are distinct outcomes. Do not promise distributed exactly-once execution; design idempotent processing and reconciliation.
9. **Do not claim tests pass from repository inspection.** The current audit did not run Maven or GitHub Actions.

## M1 entry criteria

Before adding neutral core types, complete these checks:

- Inspect the full current POS domain/application/adapter sources, persistence mappings, and all POS tests.
- Locate and inspect canonical catalog/offering, inventory, payment, finance, customer/party, tenant, branch/store, and authorization contracts.
- Inspect existing pricing and promotion public APIs, not their module names alone.
- Check current CI and the narrow POS Maven reactor; record baseline failures separately from any new change.
- Define the smallest compatible neutral contract around register/shift/sale/line/tender/receipt only after confirming there are no existing canonical equivalents.
- Add domain invariant tests and architecture guards in the same milestone PR as the implementation.

## Verification status

- Repository instructions and ADR-003 reviewed.
- POS aggregator, domain POM, and public `PosService` inspected.
- Omnichannel directory and merged PR #152 status verified.
- Pricing and promotion module directories verified.
- Full adjacent-capability contract audit: **pending**.
- Maven compilation/tests and GitHub Actions: **not run in this audit**.
- No production-readiness claim is made.
