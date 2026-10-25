# Capability Module Architecture Audit

- **Status:** Initial evidence-based audit; implementation recommendations are provisional until dependency and usage tracing is complete.
- **Repository:** `andalus-ai/numu`
- **Baseline inspected:** `main` on 2026-10-25
- **Guidance:** `AGENTS.md`, ADR-003, and `DOMAIN-CAPABILITY-MATRIX.md`

## Purpose and method

This audit evaluates whether existing modules own coherent capabilities, whether the same business concept has multiple owners, and whether capabilities are misplaced inside industry-specific or oversized modules. It is not a proposal to make every Maven artifact independently deployable or to split modules by class count.

The initial repository inventory found 11,285 tracked tree entries and 369 `pom.xml` files across multiple Maven reactor roots. This pass combines module/POM inventory with targeted source inspection. It is **not yet a complete dependency, runtime-use, schema, or API compatibility audit**. Findings are labeled by confidence and must be validated against consumers, persistence schemas, tests, and migration paths before code is moved or deleted.

## Findings

### AUD-001 — Document metadata/lifecycle and binary storage are separated, but storage implementations are duplicated

**Confidence: High that overlapping implementations exist; medium on the final migration boundary.**

Evidence:
- `document/numou-document-adapter` contains `LocalDocumentStorage` and `S3DocumentStorage`, both implementing `DocumentStoragePort`.
- `documents/numou-document-storage-spi` defines a provider-neutral `DocumentStorage` contract.
- `documents/numou-document-storage-local` contains `LocalFileDocumentStorage`.
- `documents/numou-document-storage-s3` contains `S3CompatibleDocumentStorage`, explicitly supporting AWS S3 and S3-compatible providers such as RustFS.
- The document capability also contains upload sessions, document versions, access checks, and outbox behavior, which are not the same responsibility as storing bytes.

**Assessment:** The separation between document lifecycle/metadata and binary-object storage is sound. Maintaining two parallel sets of local and S3 storage implementations is a likely duplicate ownership problem, even though their contracts and upload semantics are not identical.

**Recommendation:**
1. Make `documents/numou-document-storage-spi` the canonical binary storage port and keep local/S3-compatible provider implementations there.
2. Have the document lifecycle capability depend on a narrow storage contract through an adapter/bridge, rather than owning a second provider implementation.
3. Preserve document-level upload-session, authorization, versioning, checksum, and event semantics in the document capability.
4. Before migration, compare limits, streaming behavior, checksum validation, tenant-key construction, signed URL behavior, delete semantics, and failure mapping. Keep compatibility adapters until existing consumers and stored keys are migrated safely.

Relevant paths:
- `document/numou-document-adapter/src/main/java/tech/kayys/numou/document/adapter/outbound/storage/`
- `documents/numou-document-storage-spi/`
- `documents/numou-document-storage-local/`
- `documents/numou-document-storage-s3/`

### AUD-002 — Two subscription models appear to own overlapping lifecycle concepts

**Confidence: High that two models exist; medium-high that the older model should be retired or narrowed after consumer tracing.**

Evidence:
- `finance/subscription/numou-subscription-core` contains a Panache/JPA `Subscription` entity tied to `BillingAccount` and `Product`, including negotiated pricing, tax calculation, billing cycle, and subscription status. The module also contains its own resource/service/repository model.
- `commerce/subscription/numou-subscription-domain` contains a domain aggregate for subscription lifecycle, referencing a `ProductOfferingId`, customer reference, renewal policy, billing cycle, quantity, status, and entitlement period.
- The commerce aggregate explicitly describes the flow as Product → Offering → Subscription → Billing → Entitlement and says billing belongs to the finance capability.

**Assessment:** There are two different implementations with the same central concept and lifecycle vocabulary. They are not identical: the older implementation mixes persistence and billing calculations into the entity, while the commerce implementation uses a domain aggregate and separates billing. Keeping both as active authorities risks divergent cancellation, renewal, pricing, tax, and status rules.

**Recommendation:**
1. Treat `commerce/subscription` as the candidate owner for commercial subscription lifecycle and entitlement policy.
2. Treat finance as owner of billing accounts, invoice/receivable obligations, tax calculation where appropriate, and collection—not a second subscription aggregate.
3. Trace API, database, and application consumers before deciding whether `finance/subscription` should be deprecated, migrated behind an anti-corruption adapter, or narrowed to a clearly named billing-plan/contract capability.
4. Do not delete or rename the older module until compatibility and data migration are planned.

Relevant paths:
- `finance/subscription/numou-subscription-core/src/main/java/tech/kayys/subscription/`
- `commerce/subscription/numou-subscription-domain/src/main/java/tech/kayys/numou/commerce/subscription/domain/`

### AUD-003 — Currency/exchange-rate ownership is split across finance common and accounting capabilities

**Confidence: High that parallel models exist; medium on which model should be canonical.**

Evidence:
- `finance/common/numou-finance-currency` contains persistence-oriented `Currency` and `ExchangeRate` entities, repositories, services, and REST resources; its exchange-rate entity uses a unique currency-pair/effective-date constraint.
- `finance/accounting/accounting-capabilities/numou-accounting-currency` contains immutable domain records and FX conversion contracts, including an `ExchangeRate` with source/target currencies, rate type, status, effective timestamp, and source provider.

**Assessment:** This may be a valid separation between currency master data/rate ingestion and accounting valuation policy, but the boundary is not explicit enough to prevent duplicated exchange-rate semantics and validation.

**Recommendation:**
1. Define one owner for currency identity/code and governed exchange-rate records.
2. Keep accounting-specific valuation, rate selection, conversion rounding, and posting policy in accounting if those invariants are genuinely accounting-specific.
3. Make accounting consume a documented rate-provider contract; do not duplicate currency master data or exchange-rate lifecycle state.
4. Compare effective-date semantics, precision, rate direction, rate types, status transitions, and audit provenance before consolidating models.

Relevant paths:
- `finance/common/numou-finance-currency/`
- `finance/accounting/accounting-capabilities/numou-accounting-currency/`
- `core/framework/foundation/` (inspect shared currency/money primitives during the next pass)

### AUD-004 — Scope and planning both define work-breakdown nodes

**Confidence: High that parallel concepts exist; medium-high that scope should own the hierarchy.**

Evidence:
- `core/framework/scope/scope-domain` defines `BreakdownStructure`, `BreakdownStructureId`, `BreakdownNode`, node identity, structure membership, parent relationships, kind, and tree validation.
- `core/framework/planning/planning-domain` defines a separate `WorkBreakdownNode` with ID, parent ID, code, name, and position.
- Planning also has repositories/use cases for work-breakdown progress, schedule-activity links, baselines, and snapshots.

**Assessment:** The node models substantially overlap in hierarchy identity and parent/child representation. A hierarchy is a different concern from scheduling activities and progress snapshots, so a second generic node model in planning creates a risk of two sources of truth.

**Recommendation:**
1. Make Scope the candidate owner of breakdown-structure and node lifecycle/invariants.
2. Make Planning own schedules, activity dependencies, progress observations/snapshots, baselines, and explicit links to scope node IDs.
3. Confirm whether `WorkBreakdownNode` is persisted or used by external contracts. If so, migrate through an adapter or versioned contract rather than deleting it abruptly.
4. Keep construction WBS semantics as an extension only where they add construction-specific behavior; do not require the neutral hierarchy to understand BOQ quantities.

Relevant paths:
- `core/framework/scope/scope-domain/src/main/java/tech/kayys/numou/scope/domain/`
- `core/framework/planning/planning-domain/src/main/java/tech/kayys/numou/planning/domain/WorkBreakdownNode.java`
- `core/framework/planning/planning-application/`

### AUD-005 — Construction is described as one bounded context spanning many distinct capability families

**Confidence: High that the aggregator has broad scope; medium on the correct extraction granularity.**

Evidence:
- `construction/pom.xml` describes coverage across profile/site, WBS, BOQ, progress, variations, contracts, payment certificates, procurement, equipment, workforce, HSE, quality, documents, cost/commercial, planning/4D, site logistics, handover, warranty, closeout, controls, risk/claims, RFI/collaboration, master data, design, survey, and daily field reporting.
- Existing neutral candidates already exist for Project, Scope, Quantity, Planning/Scheduling, Budget, Document storage, Supply, Workforce, and Finance.

**Assessment:** A construction extension is appropriate, but it should compose neutral capabilities instead of becoming a second owner of generic project, hierarchy, scheduling, budget, document, workforce, or procurement behavior. The description alone does not prove every area is currently duplicated; source-level ownership must be traced.

**Recommendation:**
1. Keep construction-specific BOQ revisions, takeoff/measurement rules, site execution, construction contract administration, variations/EOT, payment certificates, HSE/site safety, and construction quality workflows in the construction extension where their invariants are genuinely domain-specific.
2. Replace generic implementations with references/contracts to neutral Project, Scope, Planning, Quantity, Budget, Documents, Supply, Workforce, and Finance capabilities where appropriate.
3. Split construction into smaller capability modules only where there is a stable contract and independent lifecycle/ownership—not simply to reduce source-file count.
4. Audit construction application/domain package ownership and dependency direction before proposing the actual Maven module moves.

Relevant paths:
- `construction/construction-domain/`
- `construction/construction-application/`
- `construction/construction-spi/`
- `construction/construction-adapter/`
- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`

### AUD-006 — The repository has multiple overlapping architectural generations and reactor conventions

**Confidence: High.**

Evidence:
- The repository has multiple Maven reactor roots and 369 POM files; `core/pom.xml` and `core/framework/pom.xml` define different aggregator/build conventions.
- Some newer capabilities use domain/application/SPI/adapter layering, while older modules include persistence entities, REST resources, and business behavior in a single `*-core` artifact (for example, `finance/subscription/numou-subscription-core`).
- The repository contains many architecture and milestone documents; those documents explicitly describe target direction rather than universal implementation compliance.

**Assessment:** This is migration debt, not a reason to force all modules into identical packaging. The risk is inconsistent ownership, build entry points, dependency management, and unclear status of legacy modules.

**Recommendation:**
1. Maintain an explicit inventory of capability owners, public contracts, persistence ownership, reactor/build entry points, consumers, and migration status.
2. Mark modules as canonical, compatibility/legacy, adapter, extension, or candidate-for-retirement based on code and consumer evidence.
3. Standardize packaging only when it improves boundary clarity; do not add empty layers or split stable code solely for naming consistency.
4. Verify builds per actual reactor and dependent consumers before each extraction.

## Initial capability disposition matrix

| Area | Initial disposition | Rationale / next evidence needed |
|---|---|---|
| Document metadata and lifecycle | Keep as a distinct capability | Owns document identity, versions, upload sessions, access policy and events; separate from byte storage |
| Document binary storage | Consolidate implementations behind one provider-neutral SPI | Two local/S3 implementations exist in parallel; compare behavior and compatibility before migration |
| Subscription lifecycle | Select one canonical owner; candidate is commerce subscription | Trace endpoints, DB tables, billing integration and production consumers of legacy finance subscription |
| Currency master data / FX rates | Clarify ownership and contract | Parallel models may be master-data vs accounting valuation, but shared lifecycle semantics need explicit ownership |
| Scope/WBS hierarchy | Consolidate generic hierarchy ownership in Scope, subject to consumer trace | Scope and Planning both define node concepts; Planning should link to scope IDs |
| Planning/scheduling/progress | Keep as a separate capability family, refine internal boundary as needed | Schedules, activity links, baselines and progress snapshots are distinct from the hierarchy itself |
| Construction-specific execution | Keep as extension; extract only stable capabilities | Domain-specific workflows are valid, but generic behavior must remain neutral |
| Legacy vs layered module packaging | Inventory and migrate incrementally | Packaging differences are evidence to inspect, not automatic grounds for rewrite |

## Next audit passes

1. **Usage and dependency tracing:** inspect POM dependency graphs (including profiles/inherited dependencies where practical), Java imports, CDI producers, REST routes, event types, schema/table names, and module consumers for each finding.
2. **Duplicate concept inventory:** search for repeated aggregates/value objects and their invariants across Project/WBS, Documents/Storage, Subscription/Billing, Currency/Money, Product/Offering/Pricing, Budget/Cost/Accounting, Identity/Organization/Tenancy, Scheduling/Workforce, and Supply/Logistics.
3. **Capability map:** record for every candidate module its business responsibility, canonical state, public contracts, direct consumers, dependencies, persistence ownership, extension points, and deployment/composition role.
4. **Disposition plan:** rank findings by duplicated invariants and migration risk; identify keep/refactor/extract/consolidate/retire, with compatibility and acceptance tests.
5. **Implementation milestones:** change one coherent ownership cluster at a time; preserve public APIs and persisted data through adapters/migrations. No module deletion or destructive migration before consumer and data evidence exists.

## Audit safeguards

- Similar names are a search signal, not proof of duplication.
- Two models may be valid when their invariants, lifecycle, authority, and transaction boundaries differ.
- Do not share a mutable aggregate merely because fields look alike.
- Do not split a capability into a service solely for deployment fashion.
- Do not claim full conformance based on POM names or architecture documents alone.
- Follow `AGENTS.md`: inspect actual contracts, tests and consumers; preserve tenant/security/idempotency behavior; do not claim builds or tests were run unless verified.
