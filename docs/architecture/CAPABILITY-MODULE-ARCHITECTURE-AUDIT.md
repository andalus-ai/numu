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


## Second pass: contract and lifecycle tracing

This pass inspects representative ports, application services, persistence models, and module POMs for the four highest-priority candidates. It refines the initial findings: overlap in implementation does not always mean the capabilities themselves should be merged.

### AUD-001 refinement — Document storage: duplicated providers, different contracts

The older document application port, `DocumentStoragePort`, is not equivalent to the newer binary-storage SPI:
- `DocumentStoragePort` exposes upload-target creation, metadata inspection, and deletion. Its upload target includes tenant/session/file metadata and an expiry.
- `documents/numou-document-storage-spi/DocumentStorage` exposes server-side streaming `put/get/head/delete`, typed `StorageKey`, object metadata, and closeable object streams.
- The newer local and S3-compatible providers depend only on the storage SPI. The older local/S3 implementations are wired as Quarkus adapters and use a separate provider-specific configuration model.
- The document adapter also owns document entities, versions, upload sessions, identity-based access checks, and outbox publishing. Those are document lifecycle concerns and should not be moved into a generic object store.

**Updated disposition:** consolidate provider implementations, not document lifecycle. The right design is likely a single canonical provider abstraction with an adapter capable of satisfying the document upload workflow (including presigned/client upload where supported). Do not mechanically replace the old port with the new SPI until differences in upload protocol, checksum enforcement, tenant-scoped keys, metadata, expiration, and S3-compatible presigning are resolved. Keep the document application port if it expresses a useful application-specific use case; adapt it to the canonical storage provider.

**Evidence limitation:** this pass did not prove all production composition roots or downstream consumers have migrated to either implementation. Before code movement, trace Quarkus bean selection, configuration keys, module consumers, and deployment manifests.

### AUD-002 refinement — Subscription: strong duplicate lifecycle ownership

The legacy finance implementation has a full persistence and API path: `Subscription`, `SubscriptionService`, `SubscriptionResource`, `SubscriptionRepository`, `BillingAccount`, and `Product`. Its service mutates subscription quantity, negotiated price, end date, and status; the entity also calculates amount, tax, and total.

The commerce capability has its own aggregate and application use cases for starting, renewing, cancelling, checking entitlements, and recording usage. It uses a subscription repository port and references a product offering by ID, keeping customer identity opaque and billing outside the aggregate.

**Updated disposition:** high-priority canonical-owner decision. Commerce subscription is the better-shaped candidate for commercial subscription lifecycle, renewal and entitlement policy. Finance should own financial obligations and billing/receivables; a billing account is not a reason for Finance to own a second subscription lifecycle aggregate.

**Still required before retirement:** identify any deployment that exposes the legacy REST resource, its database schema and table names, scheduled jobs, API clients, and billing integrations. If active, migrate via a compatibility facade and explicit data migration; do not delete the old module based only on the code shape.

### AUD-003 refinement — Currency: separate concerns are plausible; rate record authority is not

The finance-common service is a CRUD/application path for currency master data and date-effective exchange rates: it resolves currency entities by code, checks for an existing rate for a pair/date, and persists a rate entity. The accounting module instead has a framework-light `ExchangeRateProvider` contract for resolving a rate by source currency, target currency and date, plus conversion requests/results and conversion service policy.

**Updated disposition:** do not merge the whole modules. A reasonable target is:
- one authoritative currency identity/value-object vocabulary (the foundation `Currency` is already used by the accounting domain);
- one governed source of exchange-rate records and a provider adapter implementing the accounting `ExchangeRateProvider`;
- accounting-owned conversion policy, precision/rounding and financial valuation rules;
- finance-common API/persistence retained only if it is the chosen master-data/rate-management owner, with its current endpoints adapted to the canonical records.

**Unresolved risk:** the current two `ExchangeRate` models differ in effective-date representation, rate metadata, and persistence semantics. Determine whether the accounting model is a policy/value object or intended as a second stored record before deciding to extract or retire anything.

### AUD-004 refinement — Scope and Planning WBS are not yet proven duplicates

The initial comparison found a generic `BreakdownNode` in Scope and a `WorkBreakdownNode` in Planning. The second pass shows an important lifecycle difference:
- Scope exposes node and structure repositories, tree-wide validation concerns, mutable structural writes, and a structure write-lock port.
- Planning's `WorkBreakdownRepository` explicitly persists **immutable, tenant-scoped WBS snapshots**. Schedule activity linking loads a tenant's snapshot and checks that the referenced node exists before creating a schedule-to-node link.
- Planning also owns progress snapshots/baselines and schedule/activity concepts.

**Updated disposition:** do not merge these models blindly. Scope may own the canonical editable breakdown structure, while Planning owns immutable planning snapshots used for baselines, schedule associations, and reproducible progress reporting. The likely missing boundary is an explicit versioned snapshot/export contract from Scope into Planning, with stable structure/node IDs and provenance—not necessarily one shared aggregate or repository.

**Next evidence needed:** inspect `WorkBreakdownStructure`, snapshot creation/version fields, all persistence adapters, and consumers. If Planning's snapshot is genuinely immutable and versioned, preserve it as a distinct read/model artifact. If it is actually a second editable source of truth, then plan a migration to the Scope-owned hierarchy.

## Revised priority order

| Priority | Work item | Why now | Safe next action |
|---|---|---|---|
| P0 | Subscription lifecycle ownership | Two complete application/API/domain paths with overlapping lifecycle fields and operations | Trace routes, DB schema, scheduled jobs and consumers; propose compatibility/migration plan |
| P1 | Document storage provider consolidation | Two provider implementations per storage mode, but application ports have different semantics | Compare behavioral contracts and deployment composition; design one provider adapter path |
| P1 | Currency and exchange-rate authority | Master-data/rate-management path and accounting conversion path overlap at the rate record | Define authoritative records and adapter contract; preserve accounting valuation policy |
| P2 | Scope-to-Planning WBS snapshot contract | Different lifecycles may justify both models | Trace snapshot/version semantics; define versioned export/import contract rather than merging prematurely |

## Scope of this second pass

Inspected source and POMs for:
- `DocumentStoragePort`, `DocumentStorage`, local and S3-compatible storage providers, and the document adapter POM;
- legacy finance subscription entity/service and commerce subscription aggregate/repository port;
- finance-common exchange-rate service and accounting exchange-rate provider;
- Scope node repository and Planning WBS repository/activity-link use case.

This remains source-level evidence, not a full repository-wide call graph. GitHub code-search results were unavailable for reliable global consumer counts, so no claim is made that every consumer or runtime path has been found. Next work should use targeted tree/source inspection and actual reactor builds where available, and should not retire modules until usage, schema, and deployment evidence is complete.


## Third pass: legacy subscription consumer and persistence signals

The legacy Finance subscription path is not just a domain class that happens to overlap by name. Source inspection confirms several migration-sensitive behaviors:

- `SubscriptionResource` exposes `/api/subscriptions` endpoints to get by ID, list by billing account, list active subscriptions, create against a billing-account/product pair, update, and cancel.
- `SubscriptionService` contains a daily scheduled job (`0 0 0 * * ?`) that changes active subscriptions with an elapsed end date to `EXPIRED`.
- The legacy `Subscription`, `BillingAccount`, and `Product` are Panache entities. The model owns a separate product/price/tax representation, while Commerce subscription references the separate `ProductOfferingId` model.
- The legacy module's POM directly brings in REST, Hibernate ORM Panache, and Quarkus Scheduler; it is not merely a shared domain artifact.
- The recursive tree did not show dedicated SQL migration files under the legacy subscription module, and the inspected legacy module tree did not show tests. This is an inventory observation only; schema may be managed by global ORM configuration or another deployment module, and absence of visible tests is not proof of no external tests.

**Conclusion:** treat the legacy module as an active compatibility surface until deployments prove otherwise. A safe migration must account for endpoint compatibility, the daily expiry scheduler, product-to-offering mapping, billing-account references, status/period semantics, pricing/tax calculations, and existing rows. Commerce's renewal policy and current-period model are not a drop-in replacement for legacy `endDate`/status semantics.

### Subscription migration sequence (proposal, not yet implemented)

1. **Inventory deployment and consumers:** identify applications that include `numou-subscription-core`, API gateway/routes, clients calling `/api/subscriptions`, and whether the scheduled job is enabled in any environment.
2. **Establish canonical identifiers and mapping:** map legacy numeric Panache IDs, billing-account IDs, and product IDs to Commerce `SubscriptionId`, customer references, offering IDs, and billing-cycle/renewal policy. Define a stable mapping table or explicit legacy-ID field; do not assume identifiers can be cast.
3. **Specify behavior compatibility:** write characterization tests for create/update/cancel, active listing, end-date expiry, price/tax totals, and timezone/date boundaries. Compare them to Commerce start/renew/cancel/entitlement behavior and decide which behaviors are preserved, deliberately changed, or delegated to Finance.
4. **Introduce a compatibility facade:** keep the legacy REST contract while translating commands and responses to the canonical capability. Ensure the old and new schedulers cannot both mutate the same subscription population.
5. **Migrate data and observe:** run a dry-run mapping report, verify counts and orphan references, migrate with rollback/reconciliation support, and compare status/period/price outputs before switching reads/writes.
6. **Retire only with evidence:** remove the legacy module, entities, endpoints, and scheduler only after consumer inventory, migration verification, and deprecation window completion.

### Additional ownership question: Product vs Product Offering

The legacy Finance subscription's `Product` is a local sellable entity with name, price, taxable flag, and tax rate. Commerce uses `ProductOfferingId` and checks that the referenced offering is active before starting a subscription. This is another overlap candidate, but not necessarily a direct duplicate: a commercial offering may bundle catalog, lifecycle, and eligibility semantics that the old simple product lacks.

**Recommendation:** do not extract a third generic product module yet. First make the catalog/offer/price/tax ownership map explicit: commerce catalog/offering should own what is sold and its eligibility; pricing should own price plans/price resolution if present; Finance should own tax determination and financial calculation/posting policy. Keep a compatibility adapter for legacy product records until that mapping is verified.
