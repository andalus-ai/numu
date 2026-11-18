# Business Operating Platform — M01-B Domain Ownership Trace (Batch 1)

- **Status:** First source-level trace completed for four overlap candidates; no model migration authorized.
- **Repository baseline:** `main`, tree `4a3c29b56ae92f85741e8c21623e2ef5bf4c8a31`
- **Date:** 2026-11-04
- **Related inventory:** [M01-A Repository Capability Inventory](BUSINESS-OPERATING-PLATFORM-M01-REPOSITORY-INVENTORY.md)
- **Method:** Current source declarations and selected contracts were inspected. Consumer/API/schema tracing is not yet complete.

## 1. Findings

### M01-B-001 — Subscription lifecycle has two active-looking domain models

**Evidence inspected**

- `commerce/subscription/numou-subscription-domain/.../Subscription.java`: domain aggregate references a customer, a ProductOffering ID, quantity, billing cycle, renewal policy, status and entitlement period. It emits lifecycle events. Its own documentation explicitly assigns billing to Finance.
- `finance/subscription/numou-subscription-core/.../Subscription.java`: Panache/JPA entity associates BillingAccount and Product, stores negotiated price and billing cycle, and calculates amount, tax and total.

**Assessment:** This is the strongest confirmed overlap in this batch. Both models use the name Subscription and represent customer recurring commercial commitments. The older finance entity also combines lifecycle, product association and pricing/tax calculations with persistence concerns. The commerce aggregate separates offering/lifecycle from billing. However, this is not yet enough to conclude that the Finance module is unused or safe to retire.

**Provisional ownership direction:** Commerce Subscription owns commercial subscription lifecycle and entitlement-period transitions. Finance owns billing account, invoice/receivable, collection and accounting/tax posting responsibilities through explicit contracts. Finance may retain a distinct billing-plan or legacy contract model only if consumer tracing demonstrates a separate invariant.

**Required before migration:** trace REST/resource consumers, Panache repositories, table names/migrations, billing jobs, product references, API payloads, entitlement issuance, and existing records. Add compatibility adapter and data migration plan before deprecation.

### M01-B-002 — Work-breakdown hierarchy/node concepts overlap between Scope and Planning

**Evidence inspected**

- `core/framework/scope/scope-domain/.../BreakdownNode.java`: node belongs to a BreakdownStructure, references parent, has code/name/kind/position and documents that complete-tree invariants require repository/application enforcement.
- `core/framework/planning/planning-domain/.../WorkBreakdownNode.java`: neutral record with UUID, parent UUID, code/name/position and local self-parent/position validation.
- Planning application exposes `LinkScheduleActivityToWorkBreakdownNode`.

**Assessment:** The two types model substantially similar hierarchy-node data. Scope has the richer structural ownership model (explicit structure ID and kind); Planning's observed consumer links schedule activity to a planning WBS node. This suggests a potential split between hierarchy ownership and schedule linkage, not a need for Planning to own a second hierarchy.

**Provisional ownership direction:** Scope owns breakdown structures and node identity/hierarchy. Planning owns schedule activities, dependencies, baselines and progress, with links to stable Scope node IDs.

**Required before migration:** inspect planning persistence and repository ports, construction/project consumers, any serialized identifiers, and migration compatibility. Determine whether the Planning record is a value snapshot, integration DTO, or independently persisted aggregate before changing it.

### M01-B-003 — Currency and exchange-rate concepts are layered, not yet proven duplicate

**Evidence inspected**

- `core/framework/foundation/...` contains a shared Currency value object.
- `finance/common/numou-finance-currency/.../ExchangeRate.java` is a Panache persistence entity with currency relations, effective/expiry dates, active flag and a unique source/target/effective-date constraint.
- `finance/accounting/accounting-capabilities/numou-accounting-currency/.../ExchangeRate.java` is an immutable governed record with source/target Currency, positive rate, rate type, status, effective timestamp and source provider.

**Assessment:** The two exchange-rate types have different shapes and likely different roles: persisted currency/rate master data versus accounting valuation input. This could be a valid ingestion/master-data-to-accounting-policy boundary. The shared Foundation Currency is a candidate canonical currency identity primitive, but currency code validation, scale, rounding and serialization compatibility still require inspection.

**Provisional ownership direction:** keep currency identity primitive neutral; nominate one governed source for exchange-rate records; let Accounting own valuation rate selection, conversion rounding and posting policy. The master-data/provider capability should expose a stable rate-provider contract rather than duplicate accounting policy.

**Required before migration:** inspect currency entities, validation services, rate ingestion APIs, rate direction and precision, effective-date selection, consumers and migration schema.

### M01-B-004 — Document lifecycle storage port and binary-storage SPI are distinct contracts with parallel provider implementations

**Evidence inspected**

- `document/numou-document-application/.../DocumentStoragePort.java` supports upload-target creation, inspect and delete using Mutiny and an upload-session-oriented request.
- `documents/numou-document-storage-spi/.../DocumentStorage.java` supports byte-stream put/get/head/delete with a tenant-scoped StorageKey and provider metadata.
- Current tree contains local and S3 implementations under both roots: `LocalDocumentStorage`, `S3DocumentStorage`, `LocalFileDocumentStorage`, `S3CompatibleDocumentStorage`.

**Assessment:** The ports are not drop-in duplicates. The document application port models an upload workflow and remote upload target; the documents SPI models binary object I/O. The overlap is in provider implementation responsibility, not necessarily in the application contract. A bridge can preserve upload-session semantics while delegating byte storage to the shared provider SPI.

**Provisional ownership direction:** `documents` owns provider-neutral binary object storage and local/S3-compatible provider adapters. `document` owns document metadata, versions, upload sessions, authorization, checksums and lifecycle. The document adapter should delegate to the provider SPI only after verifying streaming, signed upload, checksums, key format, delete and error semantics.

**Required before migration:** compare actual implementation behavior, tenant-key construction, direct-upload and multipart support, stream lifecycle, checksum validation, content-length enforcement, signed URL expiry, object metadata and failure mapping.

## 2. Confidence and next verification

| Finding | Overlap confidence | Migration readiness |
|---|---|---|
| Subscription lifecycle | High | Low — consumer/schema trace missing |
| Scope / Planning WBS node | High | Low — persistence and consumer trace missing |
| Currency / exchange rate | Medium | Low — potentially valid layered responsibility |
| Document storage providers | High for provider overlap; low for port equivalence | Low — behavior and key compatibility trace missing |

No code, schema or API was moved in this batch. The findings identify ownership candidates and explicit verification work; they are not a mandate to delete modules.

## 3. Next source-tracing batch

1. Trace consumers and persistence for Finance Subscription and Commerce Subscription.
2. Trace Planning WBS persistence/ports and all Scope node consumers.
3. Trace currency/rate ingestion, accounting valuation and public API contracts.
4. Compare both document provider implementations and establish the storage-key compatibility contract.
5. Extend the ownership matrix only when a source-of-truth decision is supported by those traces.

M01 remains incomplete until dependency, runtime composition, tenant/security, persistence/event and deployment evidence are added.
