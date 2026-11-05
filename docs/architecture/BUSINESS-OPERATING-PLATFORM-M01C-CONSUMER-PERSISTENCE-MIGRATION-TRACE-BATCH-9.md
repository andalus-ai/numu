# Business Operating Platform M01-C — Consumer, Persistence and Migration Trace (Batch 9)

- **Repository:** `andalus-ai/numu`
- **Inspected branch:** `main`
- **Inspected tree SHA:** `f1d323723dfeade5f1c6a4850f88b58913ca8e19`
- **Date:** 2026-11-05
- **Scope:** persistence, adapters, migrations and use-case contracts for Scope/Planning WBS and Accounting/Documents
- **Change class:** architecture audit only; no production code or schema changes

## Executive result

Batch 9 materially refines the Batch 8 remediation assumptions.

1. **Planning WBS is the only WBS model with a concrete PostgreSQL persistence implementation and schema in the inspected paths.** Scope has domain, SPI and a PostgreSQL advisory-lock adapter, but no PostgreSQL repository implementation or Scope schema/migration in its module tree. Therefore, Scope is the documented target owner, but it is not yet the operational persisted source of truth. Do not migrate Planning records until Scope persistence and revision semantics exist.
2. **Planning WBS data is tenant-scoped and immutable.** Migrations V6/V7 persist structures, nodes and schedule activity links; the repository creates immutable snapshots and reads them by tenant + structure ID. Existing link foreign keys and tenant boundaries must be preserved during any future canonicalization.
3. **The shared Document capability is materially more complete than the Accounting-local in-memory service.** It has tenant-scoped PostgreSQL repositories, upload sessions, version-pinned evidence links, immutable link migrations, actor attribution and outbox. The Accounting-local DocumentService uses in-memory maps/lists and its test exercises it; the inspected module contains no accounting document persistence adapter. This is not evidence of a live production duplicate store. The correct action is to deprecate/replace the legacy test/demo orchestration, not perform a data migration from that in-memory class.
4. **Document storage and metadata are separate concerns.** Document application owns document lifecycle and evidence links; its storage port is implemented by local/S3 adapters. The separate `documents` Storage SPI identified in earlier batches needs a contract-level reconciliation with the current DocumentStoragePort before combining or deleting either interface.
5. **Scope persistence is the gating prerequisite for WBS convergence.** Until then, keep Planning's immutable WBS store operational and make cross-capability references explicit.

## A. Scope / Planning WBS: actual persistence topology

### Scope

Inspected:
- `scope-domain`: framework-independent BreakdownStructure/BreakdownNode aggregates and tree validation.
- `scope-spi`: `BreakdownStructureRepository` and `BreakdownNodeRepository` ports.
- `scope-application`: `SaveBreakdownNode` validates the complete tree under a structure write lock before saving.
- `scope-postgres-adapter`: contains `PgAdvisoryBreakdownStructureWriteLock`, which serializes writes using a PostgreSQL session advisory lock.

Important absence in the inspected module tree:
- no Scope PostgreSQL implementation of `BreakdownStructureRepository`;
- no Scope PostgreSQL implementation of `BreakdownNodeRepository`;
- no Scope Flyway migration/schema defining persisted breakdown structures/nodes.

The existing PostgreSQL adapter is therefore a coordination adapter, not a complete persistence adapter. The Scope domain and SPI establish the intended ownership boundary, but the inspected code does not yet provide a durable Scope repository.

### Planning

Inspected:
- `WorkBreakdownRepository` is explicitly tenant-scoped.
- `JdbcWorkBreakdownRepository` persists and loads immutable snapshots.
- `WorkBreakdownStructure` validates a single root, node identity uniqueness, parent membership, sibling code/position uniqueness and cycles.
- `WorkBreakdownActivityLinkRepository` and JDBC implementation persist links between WBS nodes and schedule activities.
- Migrations V6 and V7 create the WBS snapshot and schedule link tables.

Migration V6:
- `work_breakdown_structure` primary key: (tenant_id, structure_id);
- per-tenant structure code uniqueness;
- `work_breakdown_node` primary key: (tenant_id, structure_id, node_id);
- tenant + structure composite FK for parent membership;
- non-negative position, non-self-parent constraint;
- unique sibling code and position indexes;
- database triggers reject UPDATE/DELETE of structures and nodes.

Migration V7:
- links contain tenant_id, structure_id, node_id, plan_id and activity_id;
- WBS node FK is tenant/structure scoped;
- schedule activity FK preserves the referenced schedule activity;
- primary key (tenant_id, structure_id, plan_id, activity_id).

**Current operational conclusion:** Planning's WBS snapshot is persisted today in its own schema. Scope's WBS model is an intended canonical owner but lacks the durable repository path in the inspected tree. A direct cutover would be unsafe and premature.

## B. WBS convergence: safe target architecture and staged migration

### Target ownership

- **Scope:** canonical WBS structure identity, node identity, parent/child integrity, ordering, codes/labels, structure type, lifecycle and revision history.
- **Planning:** schedule activities, dependencies, baselines, progress, forecasts and immutable planning snapshots; references canonical Scope structure/node IDs.
- **Construction and other industry capabilities:** attach domain-specific work packages, quantities, trades, deliverables and controls through their own references; no private WBS copy.

### Required stages

**WBS-0 — Inventory and compatibility contract**
- Enumerate every Planning WBS consumer, API, event, table and test.
- Confirm whether immutable snapshot semantics are required for schedule baselines and whether a snapshot is a copy or a revision reference.
- Specify tenant + structure + revision + node identity as the stable reference.
- Define behavior for retired structures, deleted nodes, reparenting, and historical schedule baselines.

**WBS-1 — Make Scope persistable**
- Implement Scope PostgreSQL repositories for structure and nodes, plus migrations.
- Persist tenant ownership explicitly; current inspected Scope domain IDs and repository ports do not carry tenant ID as method parameters, so tenant context enforcement must be resolved at the application/persistence boundary before rollout.
- Enforce tenant-composite keys/FKs, sibling uniqueness, parent membership, cycle prevention, optimistic revision checks and deterministic ordering.
- Ensure the advisory-lock key includes tenant identity as well as structure identity; current lock key uses only `numou:scope:structure:<structureId>`. Global UUIDs reduce collisions but tenant-qualified locking is the stronger isolation contract.
- Add integration tests for cross-tenant ID collision attempts, concurrent tree mutations, rollback and revision integrity.

**WBS-2 — Introduce a planning-facing Scope contract**
- Planning should not import Scope persistence entities or repositories.
- Add an application port/client contract for resolving a tenant-scoped structure revision and validating a node reference.
- For distributed deployment, expose a versioned API/event contract; for modular monolith, bind the same contract to an in-process adapter.
- Planning link rows should store canonical `scope_structure_id`, `scope_revision` (or immutable snapshot ID), and `scope_node_id`, tenant-scoped.

**WBS-3 — Expand/bridge Planning schema**
- Add nullable canonical Scope reference columns alongside legacy `structure_id`/node IDs.
- Backfill mapping records only where a verified one-to-one mapping exists.
- Do not assume UUID equality means semantic equivalence; compare tenant, structure, node, code, parent, ordering, and revision.
- Add dual-read validation/metrics; keep Planning's current WBS read path as fallback during rollout.
- Reject writes that cannot resolve to the expected Scope revision.

**WBS-4 — Cut over and retire**
- Switch new Planning WBS creation to Scope.
- Migrate all consumers and verify link counts, unresolved references, tenant isolation and baseline reproducibility.
- Stop new legacy Planning WBS writes, retain historical rows for audit/rollback, and remove legacy tables only in a separately approved retention migration after the rollback window.

### WBS invariants that must not regress

- Tenant-scoped structure and node lookup.
- Immutable schedule baselines and reproducible historical snapshots.
- Parent/child integrity, single-root and cycle checks.
- Deterministic sibling ordering.
- No silent movement of a node across structures or tenants.
- Existing schedule activity links remain resolvable.
- Concurrent writers cannot validate stale trees and both commit conflicting changes.

## C. Accounting / Documents: actual persistence topology

### Shared Document capability

Inspected:
- `DocumentRepository` and `DocumentLinkRepository` ports accept tenant ID explicitly.
- PostgreSQL adapters query documents and links with tenant filters.
- Migration V1 creates tenant-scoped document, version, upload-session, link and outbox tables.
- Migration V3 adds version-pinned document links.
- Migration V4 enforces append-only document links.
- Migration V5 checks legacy duplicates before enabling idempotent unique indexes.
- Migration V6 adds optional actor attribution to evidence links.
- `DocumentStoragePort` is implemented by local and S3 storage adapters in the Document adapter.

The shared capability has durable lifecycle, versioning, tenant boundaries and evidence relationship semantics. Its document links can pin an immutable document version and preserve historical actor attribution. The outbox supports reliable event publication.

### Accounting-local DocumentService

Inspected:
- `finance/accounting/numou-accounting-application/.../document/DocumentService.java`
- `DocumentStorage.java`
- `InMemoryDocumentStorage.java`
- `DocumentServiceTest.java)

The Accounting service stores document aggregates in a ConcurrentHashMap and links in a synchronized in-memory list. It uses a local binary storage SPI, calculates SHA-256 and orchestrates document versioning/evidence links. The inspected test directly constructs this service with `InMemoryDocumentStorage`.

No production persistence adapter or database migration for this Accounting-local service was found in the inspected Accounting application paths. Thus, this service appears to be an in-memory/demo/test path rather than a second durable production document store. A full reference/wiring scan is still needed before deletion, but no database migration from its maps/lists is indicated.

### Document capability boundary

- **Document:** generic metadata, versions, upload sessions, lifecycle and evidence relationship persistence.
- **Binary storage:** provider-specific bytes, opaque storage keys, checksums/size inspection, retention and deletion behavior.
- **Accounting:** evidence policy and references from invoices, journal entries, vendor/customer records, tax evidence, audit records and other accounting aggregates.
- **Authorization:** DocumentAccessPort/Identity adapter enforces permissions; tenant-scoped keys alone are not authorization.

The current Document application uses `DocumentStoragePort` directly. The separate `documents` Storage SPI (from the previous audit) has a different API shape (`StorageKey`, put/get/head/delete and StoredObjectMetadata). Do not delete or merge it by name. First decide whether it is the reusable provider-neutral storage contract and make DocumentStoragePort an application-level upload orchestration contract over it, or whether the two interfaces should be consolidated behind a single stable port.

### Required Accounting migration stages

**DOC-0 — Consumer inventory**
- Search all production references to Accounting DocumentService, DocumentStorage and InMemoryDocumentStorage.
- Identify any public API, CDI producer, runtime module or external consumer.
- Confirm whether any binary objects are written by this path in deployed environments.

**DOC-1 — Evidence adapter**
- Add accounting application port for attaching/querying evidence references using tenant, actor, aggregate type/ID, relationship and optional document version.
- Implement adapter over DocumentLinkRepository.
- Preserve accounting-specific authorization and evidence rules; do not let Accounting query Document adapter tables directly.

**DOC-2 — Replace local orchestration**
- Route document upload/version operations through Document application commands and storage providers.
- Replace in-memory DocumentService in tests with fakes for the new application port, plus Document capability integration tests.
- Keep compatibility wrappers only if a real external caller is found.

**DOC-3 — Data/storage migration (only if evidence found)**
- If deployed legacy binary data exists, inventory keys, tenant namespaces, hashes, versions, retention and access controls.
- Copy objects idempotently; verify size and SHA-256; persist Document aggregate/version; create immutable evidence links with actor attribution where known.
- Maintain a migration manifest and reconcile missing/duplicate objects before cutover.
- Do not invent tenant IDs or actor identities for historical records; explicitly mark unknown attribution.

**DOC-4 — Retire legacy path**
- Remove the local in-memory service/SPI only after reference scan and runtime confirmation.
- No schema removal is currently proposed for Accounting because no durable Accounting-local document schema was found.

## D. Open evidence required before production work

| Question | Why it matters | Required evidence |
|---|---|---|
| Are there external callers of Planning WorkBreakdownRepository? | Contract compatibility | full repository code search, runtime module wiring and API consumers |
| Are Scope repositories instantiated anywhere? | Scope is not yet durable | CDI producers, module manifests and runtime composition |
| Does Scope need immutable revisions or mutable structures with revision history? | Data model must support schedule snapshots | Scope domain/API design and planning baseline requirements |
| Are there deployed Accounting-local document objects? | Determines whether data migration is needed | production runtime config, storage bucket/filesystem inventory and operational logs |
| Is the separate Documents Storage SPI used by Document adapters? | Avoid parallel provider abstractions | full adapter wiring and dependency graph |
| Which accounting aggregates link evidence? | Determines adapter API and authorization | consumer scan for DocumentLink and evidence queries |

## Recommended execution order

1. **WBS-1 Scope persistence contract and adapter design** — unblock canonical ownership before moving any Planning data.
2. **DOC-0 Accounting document reference/wiring scan** — low-risk clarification; likely test/demo cleanup if no production references exist.
3. **DOC-1 accounting evidence adapter design** — reuse the existing tenant-scoped immutable DocumentLink contract.
4. **WBS-2/3 compatibility bridge and data mapping** — only after Scope persistence and revision behavior are implemented.
5. **DOC-2/3/4 local service retirement and optional data migration** — conditional on consumer and deployed-data evidence.

## Verification limits

- Repository tree and source files inspected at main SHA `f1d323723dfeade5f1c6a4850f88b58913ca8e19`.
- No local checkout, SQL execution, database inspection, production runtime inspection, Maven build, tests or CI run.
- Absence statements are limited to the inspected repository tree/module paths; they are not proof that no external deployment wiring or downstream consumers exist.
- No production code, dependency, API, or schema changed.
- No PR opened.

Follow `AGENTS.md`, ADR-003 and the domain capability matrix. Preserve tenant isolation, immutable evidence, idempotency, audit attribution and historical schedule reproducibility through every migration stage.
