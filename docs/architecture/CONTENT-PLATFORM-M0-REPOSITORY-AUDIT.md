# Content Platform M0 — Repository Audit and Architecture Foundation

- **Status:** M0 audit complete; implementation not started
- **Capability:** Numou Content Platform (CMS)
- **Branch:** `content/m0-cms-architecture-audit`
- **Baseline:** `main` at `bb8a06b86a9cca19a8f7a1d88f569329abc23b8f`
- **Date:** 2026-11-18
- **Scope:** Repository evidence, domain ownership, integration boundaries, initial module plan, M1 acceptance criteria

## 1. Executive decision

Introduce a new `content/` capability reactor for editorial content management and publication. Do not place CMS behavior in `document/`, `documents/`, `asset/`, or `workspace/`.

The repository currently has no `content/` path and no CMS-specific architecture document. Existing capabilities provide useful infrastructure and adjacent products, but none is a substitute for editorial content lifecycle and publication.

The CMS is a reusable business capability, not a website-only application and not a mandatory microservice. Its domain and application contracts must remain deployment-neutral and be composable as:
- an embedded capability/library;
- a standalone CMS application;
- part of a modular monolith;
- a distributed service behind versioned API/event contracts.

This follows the repository's accepted ADR-003 and Capability Packaging and Composition Contract.

## 2. Repository facts observed

The audit inspected the repository tree, root `AGENTS.md`, the relevant capability POMs and READMEs, the domain capability matrix, and ADR-003.

### 2.1 Repository/build structure

- The repository contains multiple Maven reactor roots; there is no repository-root `pom.xml`.
- Top-level capability reactors generally inherit `tech.kayys.numou:numou-framework:0.1.0-SNAPSHOT` through `../core/framework/pom.xml`.
- New capability work must preserve independent reactor boundaries and must not assume a single root Maven build.
- Root GitHub Actions workflows are under `.github/workflows/`; CI must invoke the actual reactor path.

### 2.2 Existing neighboring capabilities

| Existing path | Observed responsibility | CMS decision |
|---|---|---|
| `document/` | Document identity, metadata, versions, classification, links, upload/scanning and document events | Reuse through public contracts for attached binary documents/evidence; do not store article/page lifecycle here |
| `documents/` | Provider-neutral binary object storage SPI with local and S3-compatible adapters/configuration; README documents RustFS-compatible external storage | Use for binary bytes; CMS owns editorial media metadata, usage/reference semantics and media lifecycle only where not already owned elsewhere |
| `asset/` | Fixed-asset accounting, depreciation, lease accounting, construction-in-progress and related asset records | **Not** a digital media library. Do not depend on it for CMS images/video/audio |
| `workspace/` | Workspace/space/page, typed blocks, page revisions, comments, links, templates, structured databases, search and collaboration | Reuse/integrate for internal knowledge collaboration where appropriate; do not make public editorial publication depend on Workspace Page semantics |
| `core/framework/workflow/` | Workflow API and Gamelan adapter/scheduler | Integrate through the public workflow contract for editorial review/approval; CMS retains publication invariants |
| `tenancy/` | Tenant lifecycle, membership and tenant settings | Consume stable tenant context/contracts; do not create CMS-specific tenant master data |
| `identity/` and `security/` | Identity/principal and authentication/authorization capabilities | Use host-established actor and authorization context; do not accept tenant/actor identity from content DTOs |
| `history/` | Historical chronology, events, entities and graph capabilities | Optional projection/integration for historical content/timeline experiences; not the authoritative editorial revision store |
| `notification/` | Notification capability | Use for publication/review notifications through its public contract |

### 2.3 Important overlap findings

**Document vs Documents:** these are distinct existing reactors. `document/` owns document records, versions, metadata and access/link behavior. `documents/` owns provider-neutral binary storage adapters. CMS should reference these contracts rather than recreate binary storage.

**Workspace vs CMS:** Workspace already has a substantial Notion-like knowledge model (`Page`, `PageBlock`, `PageRevision`, `PageTemplate`, comments, links, databases and collaboration). CMS needs its own editorial content identity, content schema, publication state, published revision pointer, channel delivery and public-facing URL semantics. A Workspace page is not automatically a CMS article or website page.

**Asset naming collision:** `asset/` is an enterprise fixed-asset accounting capability. It is not the digital asset management (DAM) concept used by media libraries. Avoid naming or dependency choices that conflate these domains.

## 3. Ownership decision

The new Content Platform owns:

- editorial content identity and content lifecycle;
- content types and editorial schema definitions;
- content entries, content relationships and taxonomy associations;
- immutable editorial revisions and revision selection;
- publication records and publication state transitions;
- editorial scheduling, embargo and expiry policies;
- channel-specific publication/delivery intent;
- CMS-owned media metadata, renditions metadata and content-to-media usage relationships, subject to reuse of existing media contracts discovered during M1;
- website/site/page composition metadata when the website capability is introduced;
- content-specific SEO metadata and redirects when those milestones are implemented.

The Content Platform does **not** own:

- binary object storage or generic document upload infrastructure;
- canonical person/organization identity;
- tenant lifecycle or authorization policy;
- generic workflow execution engine;
- generic notification delivery;
- general-purpose workspace knowledge pages and collaboration;
- general-purpose search infrastructure if a shared contract is available;
- accounting, subscription ledger or payment execution;
- generic historical event storage.

## 4. Proposed logical architecture

The initial implementation should use the smallest useful artifact set and split only when an independently useful contract or adapter exists.

```text
content/
  pom.xml                         # independent Maven reactor
  numou-content-domain/           # editorial invariants and domain events
  numou-content-application/      # use cases, authorization/context ports
  numou-content-spi/               # consumer-facing contracts only when needed
  numou-content-postgres-adapter/ # owned write model and migrations
  numou-content-rest-adapter/     # optional transport adapter, later M1/M5
  docs/

Future modules (not created in M0):
  content-modeling/
  content-publishing/
  content-delivery/
  content-media/
  content-localization/
  content-search-adapter/
  content-site/
  content-page-builder/
  content-webhook/
```

M0 intentionally does not create empty Maven modules or speculative code. M1 should validate the artifact layout against existing capability POM conventions and dependency constraints before implementation.

Dependency direction:

```text
Host application/runtime
  -> content application contract
       -> content domain
       -> content-owned ports
  -> selected persistence / transport / integration adapters

Content application -> public contracts of shared capabilities only
Content domain      -> no HTTP, CDI, ORM, broker, cloud SDK or host runtime
```

## 5. Core domain boundaries for M1

M1 should begin with a deliberately small content core:

| Concept | Responsibility | M1 |
|---|---|---|
| ContentId | Stable content identity | Yes |
| ContentTypeId | Stable reference to a content type | Yes, minimal fixed type metadata only |
| ContentEntry | Tenant-scoped editorial content aggregate | Yes |
| ContentRevision | Immutable revision snapshot | Yes |
| ContentStatus | Editorial lifecycle state | Yes, initial states only |
| ContentSlug | Validated URL-friendly identifier scoped by site/locale as required | Yes, avoid global uniqueness |
| ContentAuthorRef | Stable actor reference, not a copied identity profile | Yes |
| ContentRelationship | Typed reference to another content entry | Later unless needed by core invariants |
| ContentPublication | Exact revision published to a channel | M3/M6, model seam documented now |
| ContentTypeSchema | Dynamic schema/versioning | M2, not a premature generic JSON schema engine in M1 |

The exact aggregate boundary and persistence representation are M1 design work. Do not prematurely create a universal JSON document model without explicit field validation, schema evolution and query requirements.

## 6. Invariants and cross-cutting contracts

These are requirements for the capability, not claims that they are already implemented.

### Tenant and actor context
- Tenant and actor context are established by the host/authenticated request boundary.
- Commands must carry trusted tenant and actor/correlation context as required by the repository's application conventions.
- Tenant ID and actor ID supplied as arbitrary content request fields must never override trusted context.
- All reads and writes are tenant-scoped; an identifier alone is not authorization.

### Revisions
- A revision is immutable after persistence.
- Editing creates a new revision; it never mutates a published revision in place.
- A publication, once implemented, references an exact revision ID.
- Concurrent writes use an expected-current-revision/version guard and return a conflict without losing the caller's draft.

### Lifecycle and publication
- Editorial status transitions are explicit domain operations.
- Draft/review/approved/published are not interchangeable flags.
- A public delivery query must resolve only a publication explicitly made available to that channel.
- Scheduled publication must be idempotent and recoverable.
- Publishing side effects must use a transactional outbox or the repository's accepted equivalent; event consumers deduplicate by stable event ID.

### Media and documents
- Content stores references and metadata, not binary bytes.
- Upload, malware scanning, document access and storage authorization remain in the owning document/storage capabilities.
- A media reference must include enough stable identity/version information to avoid silently changing historical content when an underlying file is replaced.

### Authorization and audit
- Authoring, review, approval, publishing, preview and public delivery are distinct authorization actions.
- Preview is not public delivery and must not expose drafts to anonymous clients.
- Actor attribution and important lifecycle changes are auditable.

## 7. M1 implementation contract

M1 should deliver the smallest end-to-end editorial content slice:

1. Content domain types and aggregate invariants.
2. Create content entry.
3. Create a new immutable revision with optimistic concurrency.
4. Retrieve current draft and a specific revision within tenant scope.
5. Archive/restore only if these operations fit the initial lifecycle without weakening revision integrity.
6. PostgreSQL persistence owned by the content reactor, with explicit migrations and tenant-scoped unique constraints.
7. Application ports and trusted tenant/actor context integration.
8. Domain/application tests plus PostgreSQL adapter integration tests.
9. API surface only if the existing host/runtime conventions and authorization integration are verified; otherwise keep transport out of M1 and add it as a separate adapter milestone.
10. No public publishing endpoint until publication records, exact revision selection and public-vs-preview authorization are implemented.

### M1 acceptance criteria

- The content reactor builds independently using its own `pom.xml` and the shared framework parent.
- Domain tests run without CDI, HTTP, PostgreSQL or broker startup.
- A content entry cannot be read or mutated across tenants.
- A revision cannot be modified after creation.
- Two concurrent updates using the same expected revision cannot both succeed.
- A failed persistence transaction leaves no partial revision/current-pointer update.
- Application code depends only on content domain and stable public contracts.
- No dependency on Workspace persistence entities, Document persistence entities or fixed-asset classes.
- PostgreSQL migrations apply cleanly to an empty database and upgrade an existing supported schema.
- CI command and result are recorded accurately; no build/test claim is made before execution.

## 8. M0 risks and decisions to carry forward

| Risk/question | M0 decision | Follow-up |
|---|---|---|
| Duplicate Workspace Page model | Keep separate editorial aggregate; integrate only via explicit adapters/contracts | M1/M9 |
| Binary storage duplication | Reuse `documents` storage SPI and `document` metadata/access contracts where appropriate | M1 integration spike |
| Fixed asset vs digital asset ambiguity | Do not use `asset/` for DAM | M4 media architecture |
| Dynamic schema over-generalization | Start with small typed core; design schema evolution in M2 | M2 |
| Public draft leakage | Separate preview and public delivery authorization | M3/M5 |
| Multi-channel publication consistency | Exact revision + publication record + outbox | M3/M6 |
| CMS frontend choice | No framework selected in M0 | M10/M11 |
| Search engine choice | Reuse shared search contract if suitable; avoid binding the domain to a vendor | M13 |
| Runtime packaging | Same capability rules; composition profiles selected by host | M20 |

## 9. M0 verification record

Repository evidence inspected:
- Root `AGENTS.md`.
- `docs/architecture/ADR-003-reusable-capability-modules.md`.
- `docs/architecture/CAPABILITY-PACKAGING-AND-COMPOSITION.md`.
- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`.
- `document/pom.xml` and `document/README.md`.
- `documents/pom.xml` and `documents/README.md`.
- `asset/pom.xml`.
- `workspace/pom.xml` and `workspace/WORKSPACE-UI-INTEGRATION.md`.
- `history/pom.xml`.
- Source tree inventory for Document, Documents, Workspace, Asset, Workflow, Identity, Security and Tenancy.
- Root workflow inventory under `.github/workflows/`.

Observed:
- No existing `content/` reactor or CMS-specific architecture document in the inspected tree.
- Existing Document, Documents and Workspace capabilities are substantial and must be integrated through their contracts.
- Existing `asset/` is fixed-asset accounting, not DAM.
- No repository-root Maven POM exists.

Not performed:
- No Maven build or test was run in this remote repository audit.
- No Java implementation, schema migration, API, frontend, or deployment runtime was added.
- No CI result is claimed.
- No pull request has been merged.

## 10. Exit criteria and next milestone

M0 is complete when this ownership and integration contract is reviewed and accepted.

**Next:** M1 — Content Core domain and application foundation. Implement the initial aggregate, immutable revision model, tenant/actor context ports, persistence contract and focused tests. Keep M1 separate from dynamic content modeling, editorial workflow and public publishing.
