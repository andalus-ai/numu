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


## Fifth pass: document storage configuration is not yet composed into the document runtime

A targeted POM, factory, settings, and adapter pass identifies a concrete integration gap beyond the previously noted provider overlap:

- The `document/` aggregator includes `numou-document`, `numou-document-application`, and `numou-document-adapter`. Its adapter directly depends on the application module and the AWS SDK, and contains the older `LocalDocumentStorage` and `S3DocumentStorage` implementations.
- The separate `documents/` aggregator includes the new storage SPI, local provider, S3-compatible provider, and configuration factory. The configuration module depends on those providers and exposes `DocumentStorageProviderFactory.create(settings)`.
- The inspected `document/numou-document-adapter/pom.xml` does not depend on `numou-document-storage-config` or the new SPI. Its runtime properties select the older implementation with `numou.document.storage=s3` in the prod profile; local storage is a default bean outside prod. The new settings record and factory are therefore not shown as the composition path for this adapter.
- The contracts differ materially. The legacy `DocumentStoragePort` creates upload targets and supports metadata inspection/deletion, with tenant/session-scoped keys, expiry, upload token, expected size, and optional SHA-256 validation. The newer `DocumentStorage` SPI exposes object `put/get/head/delete`. The new S3-compatible provider is not a drop-in replacement for the upload-target protocol.
- The new factory's `EXTERNAL` default endpoint is a local RustFS development endpoint; its settings reject non-HTTPS endpoints when `production=true`. These safeguards only apply when a host actually constructs and uses `DocumentStorageSettings` and the factory.

**Conclusion:** the repository has both a configurable, provider-neutral storage subsystem and a document runtime with its own provider-selection path. The evidence supports an integration gap, not a claim that the new providers are entirely unused everywhere. Do not simply add a dependency or replace the bean: doing so could remove presigned upload support, upload-session checks, checksum validation, or document lifecycle guarantees.

### Safe integration sequence

1. **Choose the canonical composition boundary:** either add a bridge adapter from `DocumentStoragePort` to the new storage SPI, or extend the new SPI with an explicit upload-session/target protocol. Prefer the bridge if the existing upload API is stable and the new SPI is intended to remain a generic byte-object contract.
2. **Preserve lifecycle guarantees:** tenant-scoped key generation, expiry, single-use upload sessions, content-type/size enforcement, checksum verification, and authorization must remain enforced at the document application boundary. The generic provider must not become an authorization boundary.
3. **Preserve S3-compatible upload semantics:** confirm how the adapter obtains a presigned PUT URL, required headers, endpoint-aware URL generation, path-style access, and RustFS-compatible signing. The current generic factory constructs an `S3Client` but the inspected code does not construct an `S3Presigner`; this is a specific gap to resolve if the canonical path needs presigned uploads.
4. **Unify provider selection:** bind `DOCUMENT_STORAGE`/application configuration to the settings type in one runtime composition module, make the legacy property behavior explicit, and fail startup on contradictory or incomplete settings. Avoid two independent selectors for the same runtime.
5. **Add compatibility tests before switching:** run the same upload-target lifecycle tests against local storage and RustFS/S3-compatible storage, including expired/replayed tokens, size/checksum mismatch, tenant key isolation, deletion, unavailable provider startup, and presigned URL/header behavior.
6. **Verify composition and deployment:** inspect the actual deployable module/reactor and runtime CDI wiring, then run the narrow document reactor tests plus an integration test against a local RustFS-compatible service before deprecating either implementation.

No provider was replaced and no storage contract or runtime wiring was changed in this audit pass.


## Sixth pass: currency identity and FX rate governance

The repository contains three distinct responsibilities that should not be collapsed into one module:

1. **Foundation currency value object** — `core/framework/foundation/.../Currency.java` normalizes a three-letter code and is suitable for dependency-light domain APIs. It does not provide display metadata, active/inactive lifecycle, or currency-specific decimal precision.
2. **Finance currency registry and rate administration** — `finance/common/numou-finance-currency` persists currencies (including name, symbol, decimal places, active flag) and date-effective rates in Panache entities, exposes REST resources, and resolves rates by date with expiry checks.
3. **Accounting FX policy** — `finance/accounting/accounting-capabilities/numou-accounting-currency` defines governed immutable rate records with rate type/status/effective timestamp/source provider, conversion requests with an explicit effective timestamp and rounding policy, and an `ExchangeRateProvider` boundary used by the accounting revaluation engine.

These are not simple duplicate modules. The likely target is to reuse the foundation `Currency` identity across domain boundaries, retain a registry/administration adapter for currency metadata, and keep accounting-specific rate types, approvals, valuation and rounding policy in Accounting. A provider adapter can bridge the finance rate store to `ExchangeRateProvider` only after rate semantics and approval rules are aligned.

### Concrete correctness risk in the inspected accounting conversion implementation

The current `DefaultFxConversionService` stores registered rates in a map keyed only by source currency, target currency, and rate type. Although `ExchangeRate` contains `effectiveAt`, and `FxConversionRequest` requires an `effectiveAt`, the inspected conversion method does not compare those timestamps when selecting a direct or reciprocal rate. Registering another rate for the same pair/type replaces the previous one. Consequently, this in-memory implementation cannot represent historical rate selection and may return a rate that is not valid at the requested time. The tests should be reviewed and extended to cover this invariant before this service is used for historical accounting conversion. This is a source-level finding, not a claim that every production provider has the same defect.

Additional semantics that need explicit mapping:
- Finance rates use `LocalDate effectiveDate`, optional `expiryDate`, and an `active` boolean; Accounting rates use `Instant effectiveAt`, `ExchangeRateType`, and a richer lifecycle status.
- Finance has a unique constraint on currency pair and effective date, which may be too restrictive if multiple rate types/sources must coexist; Accounting's model permits rate types but does not itself define persistence uniqueness.
- Finance conversion chooses target decimal places from registry metadata with a fallback of 2 and uses `HALF_EVEN`; Accounting conversion takes a caller-provided `MonetaryRoundingPolicy`. These policies must remain explicit rather than being silently substituted.
- Foundation's three-letter validation is syntax validation, not a canonical ISO-4217 registry check. Finance explicitly allows custom codes such as loyalty points. Do not force all existing custom currencies into an ISO-only rule without a compatibility policy.

### Recommended next steps

1. Add characterization tests for the accounting converter: effective timestamp before/at/after rate validity, multiple historical rates per pair/type, status transitions, direct-vs-reciprocal precedence, and rate source/audit identity.
2. Correct the in-memory implementation so rate selection honors the request timestamp and governed rate lifecycle, or explicitly constrain/document it as a test/demo implementation and ensure production uses an `ExchangeRateProvider` implementation that does.
3. Define one adapter contract from the Finance rate registry to Accounting's provider. Decide timezone/date cutover explicitly when mapping `Instant` to `LocalDate`.
4. Separate currency metadata registry from currency identity; keep conversion precision, rounding, revaluation, and journal-posting policy in Accounting.
5. Inspect downstream consumers and migrations before changing table constraints or consolidating persistence.

No FX service or schema was changed in this audit pass.


## Seventh pass: Product, Offering, Catalog, and Pricing ownership

This pass finds a useful target boundary, but also several compatibility models that must not be collapsed by renaming or deleting classes alone.

### Current ownership evidenced in source

- **Foundation Product** — `product/product-domain/.../product/Product.java` explicitly owns product identity, code/name/description, product type/status, and external identifiers. Its class documentation explicitly excludes price, tax, inventory, promotion, subscription, customer, channel, and GL account. This is the strongest canonical identity/lifecycle model in the inspected set.
- **Commerce ProductOffering** — `commerce/offering/.../ProductOffering.java` references a foundation `ProductId` and owns commercial availability context: name, offering type, channel, optional seller, validity range, and offering status. That is distinct from the product's identity and lifecycle.
- **Commerce Pricing** — `commerce/pricing/.../PriceList.java` owns a tenant-defined coded list and price entries keyed by `ProductOfferingId`; each `PriceEntry` stores a `Money`. The application has separate `PriceBookPort` and `OptionPricePort` contracts. This is a sensible separation from the product aggregate.
- **Catalog Listing** — `product/product-catalog/.../CatalogListing.java` references foundation `ProductId` and owns catalog placement, publication status, display order, and title/description overrides. That is a distinct catalog concern.
- **Legacy CatalogProduct** — `product/product-catalog/.../CatalogProduct.java` is deprecated, but still combines product identity/name/description with a price, SKU, stock level, and catalog lifecycle. Its own comments say it is retained for source compatibility. It should be treated as a migration/compatibility model, not evidence that the foundation Product should regain price or stock fields.
- **Legacy subscription Product** — `finance/subscription/numou-subscription-core/.../Product.java` is a Panache persistence entity with name, price, taxable flag, and tax rate, and `Subscription.calculateTotal()` consumes its price. This is a separate legacy billing/subscription model, not the canonical commerce product contract.
- **Accounting product port** — `finance/accounting/numou-accounting-application/.../ProductCatalogPort.java` describes a Catalog lookup for invoicing but its returned `ProductDetails` includes `unitPrice`, `currencyCode`, `taxCode`, and `taxable`. That may be a deliberate invoice snapshot contract, but it currently bundles catalog descriptive fields with price/tax facts and needs explicit provenance/ownership.

### Concrete pricing-resolution contract gap

The inspected `PricingContext` carries `offeringId`, `quantity`, `customerRef`, `channel`, `configuration`, and `at`. However, `PriceBookPort.findBasePrice` accepts only `offeringId`, and `ResolvePriceHandler` calls that port with only the ID. The handler also checks that the offering is ACTIVE but does not consult its validity range or compare the request's channel/time to the offering. Option adjustments are looked up by offering plus option group/code; the inspected `OptionPricePort` call does not pass quantity, customer, channel, or effective time either.

This means the visible application contract cannot select a context-specific base price or option adjustment using the full pricing context. It may be acceptable for a deliberately flat in-memory implementation, but it is a correctness/contract limitation if the same resolver is expected to support scheduled prices, channel/customer segmentation, quantity tiers, or time-bounded offers. No claim is made that every production adapter has the same behavior; the application port signatures themselves currently prevent those adapters from receiving the missing context.

### Target ownership and migration rules

1. Keep foundation Product as identity and lifecycle; keep channel/seller/validity and commercial availability in Offering; keep price-list and adjustment rules in Pricing; keep placement/presentation in Catalog.
2. Do not move legacy price/tax fields into foundation Product. Billing and Accounting may snapshot the effective commercial facts onto an invoice, but the contract should identify the source, currency, tax determination result, effective timestamp, and immutable snapshot/version used.
3. Treat `CatalogProduct` and subscription's Panache `Product` as compatibility models until consumers, persisted rows, endpoint contracts, and deployment composition are inventoried. Preserve adapters and migration paths rather than deleting classes based only on deprecation or package names.
4. Define a pricing-resolution context contract that makes time, channel, quantity, customer/segment, currency, and selected configuration available where rules require them. Fields should be optional only when the business semantics explicitly say the dimension is irrelevant; avoid silently ignoring supplied context.
5. Validate offering availability at the requested effective time and channel in a clear owner: Offering can expose a domain-level eligibility decision or Pricing can call an explicit offering-eligibility contract. Do not duplicate the Offering state machine inside Pricing.
6. Add characterization tests for active-but-not-yet-valid and expired offerings, channel mismatch, historical price selection, quantity/customer segmentation, option adjustments, and currency consistency before widening the API. Keep a flat-price adapter for simple compositions.
7. Trace the Accounting invoice adapter and legacy subscription consumers before deciding whether invoice price/tax fields are snapshots or lookups. Tax calculation/financial posting remain Finance responsibilities; catalog description and product identity should not become tax policy.

### Recommended next implementation milestone

Make the pricing context contract truthful end-to-end: first tests that pin current behavior, then evolve the price/adjustment lookup ports to accept a deliberately designed resolution context (or a narrower immutable lookup key derived from it), enforce offering eligibility at the requested time/channel, and provide compatibility overloads/adapters for current callers. Do not add persistence/schema changes until the existing adapters and deployable compositions are traced. The legacy Product cleanup should be a separate migration milestone after consumer and data evidence.

No production code, database schema, or runtime wiring was changed in this audit pass; this is source-level architecture analysis only.


## Eighth pass: Budget, Cost, Project Controls, and Accounting boundaries

### Source-level ownership map

- **Budget control capability** (`budget/budget-domain`, `budget/budget-application`) models budget versions/lines, commitments, actual amounts, availability checks, and control outcomes. The legacy-style `Budget` aggregate additionally tracks allocated, pre-encumbered, encumbered, and actual-spent `Money` against tenant, ledger, fiscal year, cost center, and account references.
- **Planning cost evidence** (`core/framework/planning/planning-domain`) exposes approved, tenant-scoped evidence for budget-at-completion tied to a WBS node and baseline, plus cumulative actual-cost evidence as of a reporting date. Its adapter explicitly separates Planning from Finance/ERP/BoQ APIs. These are reporting inputs, not a second accounting ledger.
- **Accounting Cost capability** (`finance/accounting/accounting-capabilities/numou-accounting-cost`) models cost centers, cost pools, cost entries and allocation basis. `CostAccountingService` currently keeps its state in in-memory maps/lists, records cost entries, allocates pools, and reports cost-entry totals. It should not be treated as durable financial posting infrastructure without inspecting its adapters/composition.
- **Construction cost** (`construction/construction-domain`) has a `ConstructionCostEntry` associated with project, optional site/WBS node, construction cost type/source and amount. It is construction-context evidence and should remain an extension-specific source/record unless usage tracing proves it is intended as the authoritative financial transaction.
- **General ledger/accounting** remains responsible for journal posting, financial-period controls, reconciliation, and statements. A budget control decision is not itself a journal posting, and a cost allocation is not authoritative merely because it produces a distribution map.

### Distinct models are not automatically duplicates

The repository contains at least two budget-shaped domain models with different semantics:

1. `Budget` uses `Money` and explicit tenant/ledger/fiscal-year/cost-center/account references, with pre-encumbrance, encumbrance and actual-spend balances.
2. `PlanningBudget` uses versioned planning lines with account code, dimensions, period and `BigDecimal` amount; the inspected model does not carry currency or tenant/ledger scope on the aggregate itself.

These are not safe to merge by name alone. The second model needs a documented scope/currency contract before it can be assumed interchangeable with ledger-aware financial control. Likewise, a WBS baseline budget, a procurement commitment, a posted actual, a forecast, and a cost-center allocation represent different facts and must retain their provenance and lifecycle.

### Concrete correctness risks found in inspected code

- **Budget dimensions are not applied consistently in control totals.** `BudgetControlEngine.check(...)` resolves the allocation using account, dimensions and period, but sums open commitments with `commitments.openFor(id, account, period)`—the inspected call omits dimensions. If the repository method aggregates across dimensioned lines, one dimension can consume another dimension's availability; if it filters dimensions elsewhere, that behavior is not expressed by this call. The port/repository contract must be inspected and tested before deciding the exact fix.
- **Currency consistency is not explicit in the legacy `Budget` checks.** `availableBalance()` subtracts `Money` amounts and `canReserve()` compares only numeric amounts. The inspected methods do not explicitly reject a request in a different currency from the allocation. Confirm `Money` arithmetic guards currencies and add aggregate-level tests so this invariant cannot depend on incidental implementation behavior.
- **Budget lifecycle/version semantics need stronger evidence.** `PlanningBudget.activeVersion()` returns the last version regardless of whether it is approved/active, while `setLine` allows edits in DRAFT or REVIEW and writes to the current version. Do not assume this already provides immutable approved baselines; tests should establish whether approved versions can be mutated through version creation/state transitions and repository persistence.
- **Actual recording is a thin write path.** `ActualService.record` validates only that the amount is nonnegative before delegating to `ActualRepository.add`. The contract shown does not convey source reference, currency, transaction time, accounting status, idempotency key, or reversal/correction semantics. It may be an intentionally small planning aggregate, but it is not enough by itself to establish accounting-grade actuals.
- **Cost allocation lacks durable, explicit posting semantics in the inspected service.** `executeAllocation` calculates shares rounded to four decimal places, records entries sequentially, then clears the pool. The inspected method does not show a transaction boundary, durable allocation run/idempotency identifier, remainder reconciliation, or rollback/compensation if recording fails partway through. Do not use this implementation as proof of production-safe allocation without adapters and transaction tests.

These are risks visible in the inspected source, not a claim that every deployment uses these in-memory paths or that no other layer supplies controls.

### Target contracts and boundaries

1. **Budget owns authorization of planned spend**: allocation, available balance, budget dimensions, commitment reservation/release/consumption, control policy and budget version/baseline rules.
2. **Procurement owns procurement lifecycle**: requisition and purchase order statuses. Budget receives explicit reservation/encumbrance commands or events; it should not infer purchase state from private procurement tables.
3. **Accounting owns posted financial facts**: journals, reversals, fiscal-period status, currency conversion policy, and reconciliation. A budget's actual-spend view should be reconciled/derived from explicitly identified accounting sources rather than treated as a competing ledger.
4. **Cost accounting owns allocation policy and traceability**: cost pools, allocation bases, versioned weights, allocation runs, deterministic rounding/remainder handling, and links to source and resulting journal references. Posting the resulting entries must use an explicit accounting boundary.
5. **Planning owns project-control interpretation**: baseline-aware budget-at-completion, approved cumulative actuals, forecasts, variance and earned-value/reporting calculations. It consumes source-tagged evidence; it must not write authoritative cost transactions into Finance.
6. **Construction owns construction-specific classification**: site, construction cost type/source and WBS associations can enrich evidence, but generic budgeting and accounting should not require a construction site or BOQ.
7. **Every financial evidence contract should state** tenant/scope, currency, amount precision, as-of/effective time, source reference, approval/reconciliation status, and correction/reversal semantics where applicable. Avoid generic `BigDecimal` amounts with implicit currency where the capability claims to represent financial values.

### Recommended implementation milestone

First add characterization tests for dimension-scoped commitments, cross-currency reservation attempts, approved-version immutability, partial allocation failure, rounding remainder, and duplicate actual/allocation submissions. Then fix the confirmed contract gaps in the owning modules, with explicit compatibility adapters. Before changing schemas or consolidating models, trace repository implementations, consumers, runtime wiring, transaction managers, and the accounting journal posting path.

No production code, database schema, or runtime wiring was changed in this audit pass; this is source-level architecture analysis only.


## Ninth pass: Identity, Organization, and Tenancy boundaries

This pass inspected the foundation tenant primitives, the Identity security adapter and membership contract, the Security tenant resolver, the Tenancy aggregate, and the Organization aggregate/membership model. It is a targeted source review, not a complete authorization call graph or proof of runtime bean composition.

### AUD-007 — Tenant lifecycle and tenant authorization are distinct concerns, but legacy tenant persistence overlaps Identity

**Confidence: High that separate lifecycle and authorization models exist; medium-high that persistence ownership needs clarification.**

Evidence:
- `tenancy/tenancy-domain` models `Tenant` as the platform tenant lifecycle aggregate, with provisioning/activation/suspension/deactivation, an optional `OrganizationRef`, and separate Membership, Settings, and Provisioning aggregates.
- `security/security-application/DefaultTenantResolver` resolves a requested tenant only after membership verification. It prefers a store-backed `TenantMembershipResolver` when one is available and otherwise checks the principal's embedded memberships.
- Identity implements `TenantMembershipResolver` through `IdentityTenantMembershipAdapter`, delegating to the Identity membership-management port.
- However, the Identity adapter migration `V1__identity_rbac.sql` also creates a `tenants` table and `tenant_memberships` table. The Tenancy capability has its own tenant and membership domain models and in-memory repository implementations.

**Assessment:** Tenant lifecycle/provisioning and membership authorization can be separate capabilities, but two tenant persistence/lifecycle representations may create competing authorities. The migration establishes that the overlap is more than similar naming; it does not by itself prove both schemas are independently used in production.

**Recommendation:**
1. Make Tenancy the candidate authority for tenant identity, lifecycle, settings, provisioning, and tenant-level membership lifecycle unless consumer/runtime evidence supports a different established owner.
2. Keep Identity responsible for user identity, credentials/federation, roles/permissions, and identity/security operations. It may expose a membership-verification adapter, but should not silently maintain a second tenant lifecycle table.
3. Define the membership contract precisely: authoritative active status, tenant suspension/deactivation behavior, invitation expiry, revocation timing, and cache/claim freshness.
4. Before schema consolidation, trace all Identity repository/entity references to `tenants` and `tenant_memberships`, all Tenancy adapters and runtime composition, token claims, migrations, and any external APIs. If both are live, use an explicit migration/compatibility adapter rather than deleting tables.

### AUD-008 — Organization is not the same boundary as Tenant, but the cross-reference contract needs to stay explicit

**Confidence: High on the domain distinction; medium on lifecycle synchronization.**

Evidence:
- Foundation defines `OrganizationRef` as a neutral reference type and `TenantId` as the tenant boundary primitive.
- Tenancy's `Tenant` aggregate holds only tenant identity/lifecycle and an optional `OrganizationRef`; its documentation explicitly excludes Organization details.
- Organization's `Organization` aggregate has a required `TenantId`, legal/business identity fields, and its own ACTIVE/SUSPENDED/DISSOLVED lifecycle.
- Organization membership references `OrganizationId` and `UserId`, with its own pending/active/suspended/revoked lifecycle.

**Assessment:** This separation is directionally sound: a tenant is an isolation/customer boundary, while an organization is a legal or operational entity inside that boundary. An optional tenant-to-organization reference and organization-to-tenant ownership are not necessarily duplicates. They do, however, permit lifecycle states that need an explicit policy: for example, whether a suspended/dissolved organization may remain referenced by an active tenant, and whether an organization may be moved across tenants (the current aggregate fixes its tenant ID).

**Recommendation:**
1. Keep Organization as the owner of organization/legal-unit lifecycle and organizational membership; keep Tenancy as owner of tenant lifecycle and tenant provisioning.
2. Treat `OrganizationRef` as an identifier/reference, not as a substitute for tenant authorization or proof that an organization is active.
3. Specify and test the consistency rules for the optional Tenant.organization reference and Organization.tenantId, including dissolution, suspension, reassignment, deletion/retention, and event ordering.
4. Keep cross-capability checks behind public ports/events; do not add Organization entity dependencies to the foundation domain or Tenancy aggregate.

### AUD-009 — Tenant-scoped repository contracts are stronger than the universal persistence guarantee currently evidenced

**Confidence: High that the contract exists; medium that all adapters enforce it.**

Evidence:
- Foundation's `TenantScopedRepository` contract states that operations are automatically bounded by the active execution tenant and callers do not supply tenant IDs.
- `TenantIsolation.verify` fails closed when the current tenant is absent and rejects an aggregate whose tenant ID differs.
- Foundation's adapter-level `TenantContext` uses request-scoped state when available and a `ThreadLocal` fallback outside a request, for compatibility with tests/startup/blocking worker threads. Its documentation says setting a different tenant after security binding throws.
- Construction has separate tenant-scope migration and integration-test artifacts for transaction context, pool safety, tenant isolation, and row-level security boundaries, indicating that database transaction/session scope is a separate enforcement concern.
- The existence of the generic repository contract and helper does not prove every repository adapter applies them to reads, updates, deletes, and background jobs.

**Assessment:** Tenant isolation must be enforced at multiple boundaries: verified request identity, authorized tenant resolution, application/service checks, repository query predicates, and database transaction/RLS where supported. A domain helper alone cannot prevent a missing tenant predicate in a persistence adapter. Thread-local fallback also needs disciplined clearing and must not be assumed to propagate across asynchronous/reactive execution.

**Recommendation:**
1. Audit each tenant-scoped adapter for tenant predicates on find/list/update/delete and for tenant ownership checks before writes; include not-found behavior for cross-tenant identifiers.
2. Verify tenant context propagation/clearing across reactive continuations, executor handoffs, scheduled jobs, message consumers, and request reuse. Avoid relying on a thread-local as the async context carrier.
3. Keep RLS/transaction context as defense in depth where supported, with integration tests that exercise pooled connections and rollback/exception paths.
4. Add architecture/test coverage for missing tenant context, cross-tenant ID access, stale/forged tenant claims, tenant suspension, and concurrent requests sharing pooled threads/connections.
5. Do not assume a repository is safe solely because it implements `TenantScopedRepository`; verify its concrete adapter and composition path.

### AUD-010 — Actor identity verification is explicit at one REST boundary; its coverage and trust chain need inventory

**Confidence: High for the inspected filter behavior; medium on full endpoint coverage.**

Evidence:
- `VerifiedActorFilter` applies to `api/v1/` paths, rejects anonymous requests except login/federated-login flows, derives actor identity from the authenticated principal, and rejects a supplied `X-Actor-Id` that differs from that principal.
- It canonicalizes the authenticated principal as a UUID. This prevents a caller from choosing an arbitrary actor ID at this filter boundary.
- The filter's path condition is explicitly scoped to `api/v1`; it does not itself demonstrate equivalent actor binding for gRPC, message consumers, scheduled jobs, other REST namespaces, or events emitted outside this route tree.

**Assessment:** This is a useful request-boundary safeguard, but it is not a universal identity/authorization policy. Endpoint security annotations, tenant resolution, resource-level authorization, and non-HTTP entry points must be audited separately. The authenticated subject format is also a contract: federation/provider adapters must consistently map external subjects to the platform UUID or intentionally use a separate immutable principal identifier.

**Recommendation:**
1. Inventory all inbound transports and route roots, then establish one documented actor-context contract per transport.
2. Keep authentication (who is the actor), tenant resolution (which tenant may be used), and authorization (what action is allowed) as distinct checks.
3. Test spoofed/missing actor headers, UUID canonicalization, federation subject mapping, gRPC identity propagation, and message/event actor provenance.
4. Ensure system jobs and service accounts have explicit actor types/permissions rather than fabricating user UUIDs or bypassing authorization.

## Identity / Organization / Tenancy disposition matrix

| Concern | Candidate owner | Keep distinct from | Next evidence / acceptance criteria |
|---|---|---|---|
| User identity, credentials, federation | Identity | Tenant lifecycle and organization legal data | Trace user stores, federation subject mapping, API/runtime composition |
| Tenant lifecycle, settings, provisioning | Tenancy | Organization lifecycle and user credentials | Trace tenant tables/entities and all provisioning consumers; define compatibility/migration |
| Tenant membership lifecycle | Tenancy candidate; verify current authority | Organization membership and role assignment | Reconcile duplicate membership models, invitation expiry/revocation and active-state semantics |
| Roles, permissions, policy evaluation | Identity/Security contracts | Tenant lifecycle and organization membership | Trace tenant-scoped role assignment, caches, and every transport boundary |
| Legal/operational organization and units | Organization | Tenant isolation boundary | Define tenant/organization reference consistency and lifecycle policy |
| Tenant context propagation and persistence isolation | Foundation contract + capability adapters + database enforcement | Domain ownership | Adapter-by-adapter query, async context, and RLS/connection-pool tests |

## Proposed next milestone (not implemented by this audit)

1. **Establish authority:** produce a table-level and runtime-composition inventory for `tenants`, `tenant_memberships`, Tenancy repositories, Identity membership repositories, and token/claim consumers.
2. **Characterize behavior:** add/verify tests for membership status and expiry, suspended tenant handling, organization lifecycle consistency, actor spoofing, and tenant-context propagation across reactive/background execution.
3. **Choose a migration seam:** if Tenancy is confirmed as the lifecycle owner, add a public Tenancy membership/tenant lookup contract and adapt Identity/Security to it without importing Tenancy persistence entities.
4. **Enforce defense in depth:** prioritize concrete repository tenant predicates and transaction/RLS integration tests over adding another generic helper.
5. **Migrate only with evidence:** preserve current endpoints, claims, persisted IDs, and role assignments through an adapter and data migration; do not drop Identity tenant tables before runtime and data ownership are proven.

## Scope and limitations of this pass

Inspected `AGENTS.md`, foundation tenant context/isolation contracts, Security's default tenant resolver, Identity's actor filter and membership adapter/port, the Tenancy tenant aggregate, Organization and OrganizationMembership aggregates, and Identity migration V1. This did not establish all database consumers, active runtime beans, API exposure, or deployed schema ownership. No code was changed in this pass; the section records source-backed risks and a safe evidence-gathering plan, not a claim that all adapters are insecure or that schema consolidation is already approved.


## Tenth pass: Scheduling, Workforce, Resource Planning, and Progress

This pass inspected the neutral Planning schedule/progress contracts, Planning's WBS/activity-link migration and use case, the Workforce organization reference, and Construction's labor requirement/assignment and progress measurement models. This is a targeted source review; it does not establish every module consumer or production composition.

### AUD-011 — Planning already contains a reusable schedule/progress core; construction should integrate through references

**Confidence: High for the inspected models.**

Evidence:
- `core/framework/planning/planning-domain` models schedule plans, activities, dependencies, baselines, progress observations, schedule forecasts/variance, and WBS progress snapshots.
- `ScheduleActivity` uses neutral identifiers, names, dates, a generic `WorkItemReference`, and a position; it does not require a construction-specific BOQ or site.
- `SchedulePlan` validates activity identity/code uniqueness and dependency endpoints/cycles, and captures baselines as snapshots rather than rewriting historical baselines.
- `ActivityProgressObservation` is immutable and records as-of date, percentage, recorder, idempotency key, and optional source reference.
- Planning exposes `LinkScheduleActivityToWorkBreakdownNode`, which links a schedule activity to a tenant-scoped WBS node through public application repositories. Migration V7 adds tenant/structure/node/plan/activity identity and foreign-key constraints.

**Assessment:** Planning is the natural owner for generic schedule plans, activity dependencies, schedule baselines, and generic activity progress. Construction-specific progress measurement can remain an industry extension, but it should map approved/accepted evidence into Planning's generic progress contracts rather than introducing a second generic scheduler.

**Recommendation:**
1. Keep the schedule and WBS contracts neutral; use stable references and explicit application ports for construction-specific links.
2. Specify how approved quantity-based measurements translate to percentage or quantity progress, including units, remeasurement/corrections, approval state, and as-of dates. Do not equate a BOQ quantity automatically with percent complete.
3. Preserve immutable source provenance and idempotency across the integration so replaying an approved measurement cannot double-count progress.
4. Verify all persistence adapters enforce tenant scope and prevent linking an activity to a node or plan outside the authorized tenant/scope.

### AUD-012 — Construction labor demand is currently a construction-specific model, not a general capacity planner

**Confidence: High for the inspected aggregate; medium on broader Workforce capability overlap.**

Evidence:
- Construction `LaborRequirement` is tied to `projectId` and `siteId`, with trade, worker count, required date, assignment list, and a requested/assigned status.
- `LaborAssignment` stores a worker reference, trade, and assigned date.
- In the inspected `assignWorker` method, an assignment is appended and the requirement becomes assigned when assignment count is greater than or equal to workers required. The method does not visibly reject duplicate worker assignments, enforce a positive worker requirement, validate assignment trade/date against the requirement, or prevent excess assignments.
- The separate `workforce` capability has an employment-domain `OrganizationRef`, confirming that Workforce has its own neutral domain model, but this pass did not inspect enough Workforce contracts/adapters to establish a complete capacity/scheduling API.

**Assessment:** Construction may own trade/site-specific labor demand and site assignment rules. Generic worker identity, employment, availability, qualifications, shifts, and capacity—where supported by Workforce—should not be duplicated inside Construction. The inspected aggregate exposes concrete invariant gaps worth characterizing, but this audit does not label them production defects without the command path and tests.

**Recommendation:**
1. Inspect the full Workforce public contract and consumers before deciding which generic scheduling/capacity concepts already exist.
2. Add domain tests for zero/negative requested headcount, duplicate worker references, over-assignment, trade mismatch, assignment-date policy, and reassignment/removal behavior.
3. If confirmed by product requirements, enforce requirement/assignment invariants in the aggregate and define how assignments are persisted atomically.
4. Keep construction trade, site, project, and site-safety constraints in Construction; invoke generic Workforce contracts through public ports rather than importing Workforce persistence entities.

### AUD-013 — Progress has multiple valid meanings and should not be collapsed into one universal record

**Confidence: High for the inspected source models.**

Evidence:
- Planning's `ActivityProgressObservation` records percentage completion against a schedule activity and includes recorder, idempotency, and source-reference fields.
- Construction's `ProgressMeasurement` records a dated quantity against a BOQ item and carries measurement type, remarks, and a workflow status beginning in DRAFT.
- These records answer different questions: schedule activity completion versus measured physical quantity for a construction BOQ item. A measurement can be evidence for schedule progress, but it is not automatically the same datum.

**Assessment:** A shared progress abstraction should not erase measurement units, measurement type, BOQ lineage, approval workflow, or quantity correction history. The neutral Planning model can consume a derived progress observation only after a policy or adapter establishes the conversion and approval rules.

**Recommendation:**
1. Keep raw domain evidence authoritative in the capability that owns it.
2. Use an explicit projection/adapter from accepted construction measurement evidence to Planning progress observations; carry source reference, tenant, as-of date, and idempotency identity.
3. Define correction and reversal behavior, late-arriving measurements, multiple measurements per period, and whether completion can regress after a correction.
4. Test that draft/rejected measurements do not update accepted schedule progress and that duplicate delivery is idempotent.

### AUD-014 — The current audit supports composition, not another universal Resource aggregate

**Confidence: Medium; more Workforce and Asset contracts must be inspected.**

The repository has separate domain concerns for workforce/employment, construction labor demand, asset availability/utilization, and neutral schedule activities. A worker, an asset, a work package, and a schedule activity have different lifecycles and constraints. A common identifier/reference or availability query can be useful, but one universal Resource aggregate would risk conflating employment/qualification, asset maintenance, schedule work, and project allocation.

**Recommendation:**
1. Prefer capability-owned resource records and explicit resource references.
2. Introduce shared capacity/availability contracts only after comparing semantics for time zones, calendars, shifts, reservations, utilization, qualification, maintenance downtime, and conflict resolution.
3. If a neutral reservation/capacity capability is justified, make it own only the shared invariant (for example, mutually exclusive reservation windows) and delegate eligibility/qualification to Workforce or Asset.
4. Treat schedule planning as a consumer of availability, not the owner of workforce or asset master data.

## Scheduling / Workforce / Progress disposition matrix

| Concern | Candidate owner | Keep distinct from | Evidence/acceptance criteria |
|---|---|---|---|
| Generic schedule, dependencies, baseline | Planning | Construction-specific site/BOQ fields | Baseline immutability, dependency integrity, tenant-safe persistence |
| Generic activity progress snapshots | Planning | Raw quantity measurement evidence | Approval mapping, as-of dates, source provenance, replay idempotency |
| Construction quantity measurement | Construction | Schedule percentage complete | Units, approvals, corrections, BOQ lineage, no draft-to-actual leakage |
| Employment, qualifications, worker availability | Workforce (verify public API) | Construction labor demand | Public contracts and consumer inventory; availability/qualification tests |
| Construction trade/site labor demand | Construction | Generic workforce master data | Headcount and assignment invariants; atomic persistence |
| Asset availability and maintenance | Asset | Worker availability and schedule activity | Time-window/conflict semantics and public availability contract |
| Cross-capability resource reference | Foundation/public SPI where warranted | Universal resource lifecycle | Avoid new generic aggregate unless invariants are genuinely shared |

## Proposed next milestone (not implemented by this audit)

1. Inspect Workforce's public application/SPI contracts and persistence adapters; inventory consumers of employment, worker, qualification, and availability.
2. Add focused invariant tests for Construction `LaborRequirement` and its command/persistence path before changing behavior.
3. Define one integration contract for accepted construction measurement → Planning progress observation, with provenance, idempotency, correction, and tenant scope.
4. Test Planning activity/WBS links against wrong-tenant nodes, wrong-plan activities, and repeated link commands; preserve existing uniqueness/foreign-key behavior.
5. Decide whether a generic capacity/reservation contract is warranted only after comparing Workforce and Asset availability semantics; do not add a universal Resource model preemptively.

## Scope and limitations of this pass

Inspected `AGENTS.md`, Planning's schedule plan/activity/progress observation and WBS-link use case/migration, Workforce's organization reference, and Construction's labor requirement/assignment and progress measurement aggregates. This did not establish all Workforce APIs, resource consumers, database adapters, or deployed composition. No runtime code was changed and no tests were run; findings are source-backed review items and acceptance criteria, not a claim that the full integration has been verified.


## Eleventh pass: Workforce contracts and construction integration follow-through

This pass deepened the previous review by reading the current Workforce worker, employment, qualification, worker-qualification, and timesheet aggregates; Construction labor requirement tests and handler; Construction progress measurement and command handler; and Planning's immutable progress observation and WBS/activity link use case.

### AUD-015 — Construction labor requirement invariants are now present in the inspected main-branch source

**Confidence: High for the current source and unit tests inspected.**

Evidence:
- `LaborRequirement` rejects non-positive headcount and blank trade.
- `assignWorker` requires REQUESTED status, prevents assignments beyond headcount, requires a matching trade, rejects duplicate worker IDs, and transitions to ASSIGNED only at exact headcount.
- `LaborRequirementTest` covers zero/negative headcount, blank trade, trade mismatch, duplicate workers, exact completion, and excess assignment.
- The application handler persists the aggregate and then publishes its domain events.

**Assessment:** The previously identified aggregate-level invariant gap appears addressed on the inspected `main` branch. Do not duplicate the fix. Remaining checks are whether repository persistence is atomic/optimistic-lock safe, whether tenant scope is enforced at the command/repository boundary, and whether event publication is durable when the process fails between persistence and publishing.

**Recommendation:** Keep the existing invariant tests. Add persistence/concurrency and event-delivery tests only after inspecting the concrete repository and event-publisher implementations. Prefer an outbox or established transactional event mechanism if the runtime's consistency requirements demand it.

### AUD-016 — Workforce owns worker/employment/qualification and time evidence, but no shared project-capacity contract was established

**Confidence: High for inspected aggregates; medium for complete module coverage.**

Evidence:
- `Worker` is tenant-scoped and models worker identity reference, type, status, and audit metadata.
- `Employment` models the worker's relationship to an organization, employment type, position reference, and lifecycle.
- `Qualification` models a credential catalogue entry; `WorkerQualification` records a worker's credential, issue/expiry dates, certificate reference, and revocation status.
- `Timesheet` records time for a worker/employment over a date range, with draft/submitted/approved/rejected states and entries classifying time against business contexts.
- These are useful reusable Workforce primitives, but the inspected models do not by themselves provide a cross-capability availability calendar, shift/reservation conflict check, project capacity allocation, or a complete qualification-eligibility query.

**Assessment:** Construction should reference a worker through a stable public identifier and use Workforce application contracts for eligibility where they exist. It should not import Workforce persistence entities. A generic capacity service remains unproven: timesheet actuals are not future availability, employment status is not assignment availability, and a qualification catalogue entry is not proof that a particular worker currently holds a valid qualification.

**Recommendation:**
1. Inventory Workforce SPI/application queries for worker status, employment status/date, current qualifications, and timesheet approval before designing a new contract.
2. Define an eligibility query only for policy the consumer needs, returning a stable decision/evidence result rather than exposing repositories or aggregates.
3. If future capacity is required, first specify calendar, timezone, shift, overlapping reservation, leave, assignment, and concurrency semantics; then decide whether Workforce owns availability or a separate planning capability owns reservations.
4. Keep worker master data and qualification lifecycle in Workforce, project/site/trade demand in Construction, and schedule activities in Planning.

### AUD-017 — Progress measurement recording is separate from approval and currently has no inspected Planning bridge

**Confidence: High for the inspected command and aggregate.**

Evidence:
- `RecordProgressMeasurementHandler` creates a Construction `ProgressMeasurement`, saves it, and publishes its recorded event.
- A new measurement begins in DRAFT; `submit` and `approve` are separate domain transitions. Approval raises `ProgressMeasurementApproved`.
- Planning's `ActivityProgressObservation` requires activity ID, as-of date, percentage, recorded timestamp, recorder, idempotency key, and optional source reference.
- The inspected Construction handler does not invoke Planning. The two inspected models have no direct conversion: a quantity is not inherently a percentage, and the Construction measurement does not expose the Planning observation's attribution/idempotency contract as part of the record command.

**Assessment:** Do not automatically project every recorded measurement into schedule progress. The integration should consume approved evidence only, and needs an explicit quantity-to-progress policy supplied by the relevant BOQ item/measurement basis and activity mapping. Approval-event delivery must be idempotent.

**Recommendation:**
1. Define an application-level port/consumer contract for approved measurement evidence; keep Construction independent of Planning implementation classes.
2. Require mapping metadata for BOQ item → schedule activity and a documented conversion policy (for example, accepted cumulative quantity / approved baseline quantity where units and scope match). Reject missing, incompatible, or zero/invalid denominator cases rather than guessing.
3. Derive stable idempotency identity from the measurement and its approval/revision, and preserve source reference, tenant, as-of date, and actor attribution.
4. Specify corrections/rejections/revocations and late events before treating a projection as authoritative; retain the raw measurement as source evidence.
5. Test that DRAFT/SUBMITTED/REJECTED measurements do not create accepted progress observations, approval replay is idempotent, and a correction does not double-count cumulative quantity.

### AUD-018 — WBS/activity linking validates in-memory ownership, but transaction and replay guarantees require adapter-level tests

**Confidence: High for the inspected use case; medium for the database/runtime behavior.**

Evidence:
- `LinkScheduleActivityToWorkBreakdownNode` checks that the activity belongs to the supplied authorized plan, loads the WBS structure through a tenant-aware repository, verifies the node exists in that structure, then creates the link.
- Migration V7 adds tenant/structure/node/plan/activity columns and foreign keys/constraints.
- The use case does not itself show duplicate-link handling, a transaction boundary, or concurrent replay behavior; those may be enforced by the repository/database.

**Recommendation:** Verify uniqueness and foreign keys against concurrent duplicate commands, ensure the supplied plan is loaded through an authorized tenant boundary (not merely passed by an untrusted caller), and confirm repository writes and event publication follow the application's transaction/outbox policy.

## Workforce / Construction / Planning implementation disposition

| Work item | Current disposition | Next acceptance criterion |
|---|---|---|
| LaborRequirement aggregate invariants | Present on inspected main branch; tests cover key cases | Persistence/concurrency and tenant-scope tests |
| Worker/employment/qualification reuse | Reusable aggregates exist; full public query contract not yet established | Inventory SPI/application ports and consumers before new API |
| Future worker capacity | Not established by inspected contracts | Define calendar, shift, leave, assignment, timezone and concurrency semantics first |
| Construction measurement → Planning progress | No bridge found in inspected handler/model path | Approved-evidence contract, explicit conversion policy, idempotency and correction tests |
| WBS/activity link integrity | Use-case ownership checks and database migration exist | Duplicate/concurrent command and tenant/transaction integration tests |

## Proposed next implementation milestone (not implemented by this audit)

1. Inspect concrete Construction repository, event publisher/outbox, and Planning observation repository implementations before choosing transaction boundaries.
2. Add a public, transport-neutral approved-measurement evidence contract and consumer only after confirming module dependency direction and the existing event infrastructure.
3. Require an explicit BOQ-item-to-activity mapping and a defined progress conversion policy; do not infer percentage from a raw quantity.
4. Add replay, correction, wrong-tenant, and failure-recovery tests at the integration boundary.
5. Keep the existing `LaborRequirement` invariants; extend only the missing adapter/transaction tests supported by the implementation.

## Scope and limitations of this pass

Inspected current `main` source for Construction labor requirement aggregate/tests/handler/repository port; Workforce Worker, Employment, Qualification, WorkerQualification, and Timesheet; Construction progress measurement aggregate/handler; Planning ActivityProgressObservation and WBS/activity link use case. This did not establish all concrete persistence implementations, runtime wiring, or event delivery guarantees. No runtime code was changed and no tests were run; the audit records a source-backed integration gap and next acceptance criteria.
