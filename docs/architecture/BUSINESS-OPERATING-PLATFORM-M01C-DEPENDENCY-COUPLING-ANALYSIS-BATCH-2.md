# Business Operating Platform — M01-C Dependency and Coupling Analysis (Batch 2)

- **Status:** Source-level boundary sample recorded; complete import graph and dependency remediation remain open.
- **Repository:** `andalus-ai/numu`
- **Branch:** `docs/business-platform-m01c-source-boundary-batch2`
- **Baseline:** current `main` at branch creation, 2026-11-04.
- **Related:** M01-A Repository Capability Inventory; M01-B Domain Ownership Trace; M01-C Batch 1.
- **Method:** Inspected current CRM Domain POM and selected CRM aggregate source, Accounting Application POM and document-related source, Document Application storage port, and Documents storage capability README.

## 1. Findings

### C-07 — CRM Domain → Identity Domain remains a confirmed build edge, but the sampled CRM model does not require Identity types

**Evidence**
- `crm/numou-crm-domain/pom.xml` directly declares `numou-identity-domain`, alongside Foundation and Ecosystem Domain.
- `crm/.../domain/account/Account.java` imports `tech.kayys.numou.ecosystem.domain.identifier.ParticipantId`; its aggregate models a CRM account as the commercial context for an ecosystem participant.
- `crm/.../domain/model/Customer.java` imports Foundation and CRM types only; the inspected Customer aggregate does not import Identity types.

**Assessment:** The Ecosystem dependency has a concrete observed purpose: CRM Account holds a typed Ecosystem ParticipantId. The sampled Account and Customer sources provide no evidence that CRM's domain invariants require Identity Domain. This does **not** prove the Identity dependency is unused throughout the CRM Domain package; the remaining source files and tests must be checked before removing it.

**Direction:** Keep CRM's link to an authoritative participant/party identity through the Ecosystem contract if that remains the intended owner. Authentication principal, credential, account lifecycle and authorization concerns should remain outside CRM Domain. If remaining CRM Domain files use Identity types, determine whether those are opaque identifiers or actual lifecycle behavior and move any integration to an application port where appropriate.

**Next evidence:** inspect every CRM Domain Java source and test import; identify all Identity type references; check whether any CRM aggregate invariant depends on authentication state; then add a targeted ArchUnit rule and remove the dependency only if no legitimate domain contract requires it.

### C-08 — Accounting Application contains a second document/evidence model and storage SPI

**Evidence**
- `finance/accounting/numou-accounting-application/pom.xml` directly declares `numou-document`.
- The same application source tree contains `tech.kayys.numou.accounting.application.document.DocumentService`, which imports `tech.kayys.numou.accounting.domain.document.*` rather than the shared Document Application package.
- `DocumentService` maintains documents in a `ConcurrentHashMap` and links in a synchronized in-memory list. It constructs storage keys itself and performs SHA-256 calculation before delegating byte persistence.
- `tech.kayys.numou.accounting.application.document.DocumentStorage` defines its own store/retrieve/exists/delete byte-storage SPI. `InMemoryDocumentStorage` is explicitly described as a test implementation.
- The shared `document` capability already exposes document lifecycle ports, including upload-session operations through `DocumentStoragePort`. The separate `documents` capability defines tenant-scoped binary object storage and explicitly says tenant namespace is not authorization.

**Assessment:** There is a confirmed second Accounting-specific document/evidence model and binary storage port in addition to the shared document capabilities. The POM edge to `numou-document` is real, but the inspected Accounting document service does not import the shared Document Application API. It is not yet established whether another Accounting class uses that dependency or whether the dependency is stale. The in-memory maps also mean the inspected service implementation is not, by itself, a durable production document repository.

**Direction:** Accounting should own accounting-specific evidence requirements and links to accounting aggregates, but should not independently own a second generic document lifecycle or binary object store. Prefer stable document IDs/references and an application port to the shared Document capability; shared `documents` owns binary object persistence/providers, while shared `document` owns document metadata/version/upload lifecycle. Accounting retains evidence classification, relationship semantics, retention/legal requirements only where those are genuinely accounting invariants.

**Required before consolidation:** trace all Accounting imports of `numou-document`; inspect Accounting document domain entities and migrations; establish whether evidence links are persisted or only in-memory; map storage key and tenant semantics; verify checksums, versioning, authorization, retention and failure behavior; provide a compatibility adapter and data migration plan before replacing anything.

## 2. Updated boundary assessment

| Boundary | Evidence now confirmed | Current decision |
|---|---|---|
| CRM Domain → Identity Domain | Direct Maven edge; sampled CRM Account uses Ecosystem ParticipantId; sampled Customer does not use Identity | Keep flagged; full package import audit required before dependency removal |
| CRM Domain → Ecosystem Domain | Direct Maven edge and concrete ParticipantId usage in Account | Legitimate observed contract dependency; constrain to public party/participant identity |
| Accounting Application → Document Application | Direct Maven edge; inspected Accounting DocumentService uses Accounting-local document domain/port | Potential stale or overly broad dependency; full import/use audit required |
| Accounting-local document/storage | Local domain model, service, SPI and in-memory implementation observed | Confirmed overlap candidate; no migration authorized |
| Document → Documents | Distinct lifecycle and binary-storage ports; Documents README defines provider boundary | Adapter/bridge is plausible, subject to semantics and tenant-key verification |

## 3. Architecture implications

1. A direct Maven dependency is not enough to justify a domain dependency; the imported type and invariant must be identified.
2. CRM's participant reference should not pull authentication lifecycle into the CRM aggregate model.
3. Accounting evidence is a domain-specific relationship, but generic document metadata, versions, upload sessions and bytes should have one shared owner.
4. Tenant-scoped storage keys are namespace isolation only; authorization must be enforced by the calling application boundary.
5. No module or dependency is removed in this batch. No API, event, schema or persisted data is changed.

## 4. Remaining work

1. Complete the CRM Domain import audit (all production and test Java sources), then add a narrow architecture test.
2. Complete Accounting's use audit for `numou-document`, inspect accounting document domain/repository/migration sources and determine durable versus in-memory behavior.
3. Generate the full direct internal Maven dependency graph from all reactor POMs, preserving scope and optional flags; run cycle detection.
4. Add package-level dependency checks only after the allowed contracts are documented.
5. Verify representative Maven reactors and CI; no build or CI result is claimed by this documentation-only batch.

**Decision:** C-07 is a confirmed dependency-direction question, not yet a removable dependency. C-08 is a confirmed duplicate generic document/storage implementation candidate in Accounting. Continue source and persistence tracing before any consolidation.
