# Business Operating Platform M01-C — Source Boundary Validation (Batch 8)

- **Repository:** `andalus-ai/numu`
- **Inspected branch:** `main`
- **Inspected tree SHA:** `9df3bb727ed5f185e35505405e9caaa1afc5a85b`
- **Date:** 2026-11-05
- **Scope:** source-level validation of five prioritized M01-C findings
- **Change class:** architecture audit only; no production code, API, schema, or dependency changes

## Executive result

This batch confirms two substantive boundary problems, identifies one probable ownership cleanup, and prevents two false-positive interpretations.

| Finding | Source evidence | Assessment | Decision |
|---|---|---|---|
| CRM Domain -> Identity Domain | CRM POM declares Identity; inspected CRM Account uses Ecosystem ParticipantId; inspected Customer uses Foundation/CRM only | Dependency is suspicious and likely unnecessary, but not yet proven unused across all 58 CRM domain Java files | Keep dependency until a complete source/import + test + generated-code scan proves it is unused; then remove in a focused compatibility-safe change |
| Accounting document lifecycle/storage | Accounting Application contains its own DocumentService, DocumentStorage SPI, InMemoryDocumentStorage and in-memory document/link collections; it also depends on the shared `numou-document` domain | Confirmed duplicate application-level orchestration/storage abstraction, while the shared Document capability already owns document aggregate/version/upload-session semantics | Consolidate binary storage and lifecycle orchestration behind Document/Storage public contracts; preserve accounting-specific evidence classification and aggregate links |
| Accounting Application fan-out | POM has 16 direct compile-scope internal capability dependencies plus Foundation Application | High fan-out is confirmed; a violation is not. This may be an accounting composition root | Retain as composition root only if it genuinely assembles accounting use cases; otherwise split deployable adapters/composition modules. Do not mechanically remove dependencies |
| Commerce vs Finance Subscription | Commerce aggregate owns offering reference, customer reference, quantity, billing cycle, renewal policy, status and entitlement period; Finance class is a Panache entity linked to BillingAccount/Product with negotiated price and tax calculations | Two models with overlapping subscription vocabulary but distinct responsibilities; Finance model is persistence-heavy and currently lacks a clean public contract in its POM | Commerce owns commercial subscription lifecycle/entitlement; Finance owns billing account, invoice/receivable, price/tax calculation and collection. Migrate finance model toward billing contract; do not merge aggregates |
| Scope vs Planning WBS | Scope owns typed BreakdownStructure and BreakdownNode; Planning separately owns WorkBreakdownStructure/WorkBreakdownNode and schedule-to-WBS links | Confirmed overlapping neutral hierarchy models. Planning model adds validated snapshot/tree semantics, but matrix assigns neutral WBS ownership to Scope | Scope should own canonical hierarchy identity and revisions; Planning should own schedule/activity association and planning projections referencing stable Scope IDs. Migration requires persistence and consumer inventory |

## Evidence and reasoning

### 1. CRM -> Identity Domain

Files inspected:

- `crm/numou-crm-domain/pom.xml`
- `crm/numou-crm-domain/.../domain/account/Account.java`
- `crm/numou-crm-domain/.../domain/model/Customer.java`

The CRM Domain POM directly declares `numou-identity-domain`. The inspected Account aggregate imports `tech.kayys.numou.ecosystem.domain.identifier.ParticipantId`, not an Identity type. The inspected Customer aggregate imports Foundation and CRM types only.

This supports a likely unnecessary direct dependency, but these two files do not prove that the dependency is unused by all CRM domain source, tests, generated sources, or profiles. The CRM domain tree contains 58 Java source files; a complete repository-wide import and type-reference scan is still required before deleting the POM edge. Authentication principals, credentials, identity lifecycle, and authorization should remain outside the CRM domain.

**Disposition:** unresolved removal candidate; do not remove yet.

### 2. Accounting -> Document lifecycle and binary storage

Files inspected:

- `finance/accounting/numou-accounting-application/pom.xml`
- `finance/accounting/numou-accounting-application/.../application/document/DocumentService.java`
- `finance/accounting/numou-accounting-application/.../application/document/DocumentStorage.java`
- `document/numou-document/pom.xml`
- `document/numou-document/.../domain/document/Document.java`
- `document/numou-document/.../domain/document/UploadSession.java`
- `documents/numou-document-storage-spi/.../storage/spi/DocumentStorage.java`
- `documents/numou-document-storage-spi/.../storage/spi/StorageKey.java`

Accounting's application-local DocumentService:
- keeps document aggregates in a `ConcurrentHashMap` and links in a synchronized in-memory list;
- creates storage keys itself and stores raw byte arrays through its own `DocumentStorage` interface;
- computes SHA-256 locally and handles upload/version/link orchestration;
- does not expose a durable repository boundary in the inspected class.

The shared `numou-document` module already owns a Document aggregate, contiguous version history, lifecycle status, domain events, and UploadSession with tenant ID, storage key, expected size/checksum, expiry, and completion references. The separate `documents` storage SPI owns provider-neutral binary put/get/head/delete with a tenant-scoped StorageKey and explicit metadata.

This is stronger than merely having two similarly named interfaces: the accounting application implements a parallel document lifecycle and binary storage path while depending on the shared document domain. However, the existing local implementation may be legacy, prototype, or used only by tests; its persistence consumers, wiring and production reach must be traced before removal. The accounting evidence relationship (document type/classification and aggregate linkage) is a valid accounting concern and must survive consolidation.

**Target boundary:**
- Document capability: document metadata, versions, upload sessions, lifecycle, checksums and generic document references.
- Documents storage capability: tenant-scoped binary object storage and provider adapters.
- Accounting: accounting-specific evidence meaning, evidence requirements and links from accounting records to document references.
- Authorization: enforced at application boundary; a tenant-scoped storage key is not authorization.

**Disposition:** confirmed overlap; implement a compatibility bridge and migration plan before retiring local service/storage classes.

### 3. Accounting Application fan-out

The Accounting Application POM directly declares 16 internal compile-scope modules: accounting domain, payables, receivables, consolidation, treasury, document, Islamic, MDM, multitenancy, report, workflow, tax, cost, currency, compliance, and Foundation Application.

The fan-out is real. It is not sufficient evidence of a domain-boundary violation: an application-level composition root may legitimately assemble multiple accounting sub-capabilities. ADR-003 explicitly allows composition while requiring consumers to depend only on capabilities they use.

The next validation must map each dependency to actual source imports, use cases, runtime wiring, and reactor packaging. In particular, distinguish:
- a deliberate accounting application facade/composition root;
- reusable accounting application services accidentally coupled to optional modules;
- adapters/runtime dependencies that should not be present in a portable application layer.

**Disposition:** composition review, not an automatic split/removal.

### 4. Commerce vs Finance Subscription

Files inspected:

- `commerce/subscription/numou-subscription-domain/.../domain/Subscription.java`
- `finance/subscription/numou-subscription-core/.../Subscription.java`
- both module POMs

Commerce Subscription is a domain aggregate with an opaque customer reference and ProductOffering ID. It owns quantity, billing cycle, renewal policy, subscription status, current entitlement period, and started/renewed/cancelled events. Its own class documentation explicitly states that billing belongs to Finance and consumes the Commerce aggregate through events.

Finance Subscription is a package-private Panache JPA entity linked to BillingAccount and Product. It stores negotiated price, quantity, billing cycle, dates and status, and calculates amount/tax/total. Its module POM directly includes Quarkus REST, Jackson, Hibernate Panache and Scheduler, with no explicit internal billing domain/application/SPI dependencies in the inspected POM.

These are not safe candidates for a single merged aggregate. Commerce's model is the commercial contract/entitlement lifecycle; Finance's model is a legacy billing-oriented persistence record with pricing/tax calculations. The latter's current framework-heavy packaging is a separate modernization concern.

**Target contract:** Finance consumes versioned subscription commercial facts/events or a stable application contract and creates billing schedules/invoices/receivables. Finance owns its own billing state and accounting calculations; it must not mutate Commerce entitlement state directly.

**Disposition:** ownership distinction confirmed; legacy Finance model migration needed after consumer/schema inventory.

### 5. Scope vs Planning WBS

Files inspected:

- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`
- `core/framework/scope/scope-domain/.../domain/BreakdownStructure.java`
- `core/framework/scope/scope-domain/.../domain/BreakdownNode.java`
- `core/framework/planning/planning-domain/.../domain/WorkBreakdownStructure.java`
- `core/framework/planning/planning-domain/.../domain/WorkBreakdownNode.java`
- `core/framework/planning/planning-application/.../LinkScheduleActivityToWorkBreakdownNode.java`

The capability matrix explicitly assigns hierarchical structures, stable IDs, parent/child integrity, ordering, codes/labels, structure type and revision references to Scope breakdown/WBS, and separately states that a WBS hierarchy is not a schedule.

Scope's BreakdownStructure owns type, code, name, owner reference and lifecycle; BreakdownNode carries structure ID, parent ID, code, name, kind and position. Planning independently defines a WorkBreakdownStructure and WorkBreakdownNode with its own UUID identities, root/parent/cycle/sibling validation and tree navigation. Planning's application service loads this separate structure through a WorkBreakdownRepository and links schedule activities to its node IDs.

The duplicate ownership is substantive: both are generic WBS hierarchy models. Planning's validation behavior is useful and should not be discarded; it should be moved to, or invoked through, the canonical Scope contract. Planning should retain schedule-specific links, snapshots and projections, not a second authoritative WBS identity store.

**Disposition:** confirmed overlap against the documented target architecture. Before changing code, inspect Scope and Planning adapters, schema/migrations, consumers, tenant scoping and revision semantics. A staged migration should map Planning UUIDs to Scope BreakdownNodeIds, preserve existing schedule links, and provide a compatibility read path until all consumers migrate.

## Remediation order

1. **Scope/Planning WBS:** highest architectural clarity; establish canonical Scope contract and map Planning references. Do not delete Planning storage before migration inventory.
2. **Accounting documents:** define an accounting evidence reference contract and bridge it to Document + Documents Storage. Preserve evidence classification, link semantics and auditability.
3. **Subscription:** define Commerce-to-Finance event/application contract and migrate the legacy Finance Panache model without conflating commercial entitlement with billing.
4. **CRM Identity dependency:** complete full source/test/generated-source reference scan; remove only if no legitimate public Identity contract is used.
5. **Accounting fan-out:** map the 16 edges to source imports and runtime wiring; keep intentional composition edges.

## Verification limits

- Repository files were inspected through GitHub at the tree SHA above.
- No local checkout or Maven execution was available in this audit action.
- No unit tests, Maven builds, full dependency graph, full CRM import scan, database migration inventory, or CI workflow was run.
- No production code or dependency was changed.
- No pull request was opened.
- These findings are source-evidence assessments, not a claim that all consumer and persistence migration impacts are known.

## Governing rules

Follow `AGENTS.md`, ADR-003, and the domain capability matrix. Capability ownership is an architectural target; existing code must be migrated incrementally with public contracts, compatibility, tenant isolation, audit and persistence integrity preserved.
