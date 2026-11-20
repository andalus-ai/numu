# Historical Timeline Platform — M0 Repository Audit and Architecture Baseline

- **Status:** M0 audit baseline; implementation is incremental and not implied by this document.
- **Repository:** `andalus-ai/numu`
- **Audited ref:** `main`
- **Audit date:** 2026-11-05
- **Scope:** Targeted repository and architecture audit for a reusable historical knowledge and multi-perspective timeline capability.
- **Related:** `AGENTS.md`, [ADR-003](ADR-003-reusable-capability-modules.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), [Domain Capability Ownership Matrix](DOMAIN-CAPABILITY-MATRIX.md), [Ecosystem Vision and Engineering Guardrails](../ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md).

## 1. Executive decision

Introduce a new **History capability** as an application-neutral, reusable domain capability. It owns historical chronology, historical events and periods, historical claims and source attribution, historical entity relationships, and the query model required to produce multiple timeline perspectives.

The platform must use **one canonical historical knowledge model and multiple query projections**. Global, regional, civilization, person, dynasty, political, religious, scientific, military, economic, cultural, technological, environmental, comparative, and user-defined timelines are perspectives over shared records—not separate event stores or independently authored copies.

The History capability is not a replacement for Numou's generic Event infrastructure, Social Graph, Geo, Document, or object-storage capabilities. It composes those capabilities through their public contracts where semantics fit, while retaining ownership of historical domain invariants.

This document records an architecture baseline. It does not claim that a History module, chronology engine, historical API, timeline UI, or historical dataset already exists.

## 2. Repository evidence

The following facts were checked on `main`:

| Repository evidence | Finding | Architectural consequence |
|---|---|---|
| `README.md` and `AGENTS.md` | Capability-first boundaries, neutral core, explicit dependencies, multiple Maven reactor roots, and no assumed root Maven build | History must follow the existing capability packaging and test conventions |
| `core/framework/pom.xml` | Framework reactor includes Foundation, Social Graph and Event aggregators | Reuse must be verified at public contract level; do not add History as a generic framework primitive |
| `core/framework/event/pom.xml` | Existing Event aggregator separates domain, application, SPI and adapters; its stated role is transport-neutral business-event contracts, envelopes, store/replay and publication | Do not create a competing generic event bus. HistoricalEvent is a domain record, not the platform event envelope |
| `core/framework/event/event-domain/pom.xml` | Event domain depends on Foundation | History may publish integration events through Event contracts, but its historical records remain History-owned |
| `core/framework/social-graph/pom.xml` | Existing Social Graph has domain, SPI, application and JDBC persistence artifacts | Evaluate its graph semantics for people/organization relationships; historical temporal and evidentiary relations may require History-owned relation types |
| `core/framework/social-graph/social-graph-domain/pom.xml` | Social Graph domain depends on Foundation and Ecosystem domain | Do not assume its existing graph model can represent every historical relation or temporal validity; inspect source contracts in M1 |
| `geo/pom.xml` and `geo/geo-domain/pom.xml` | Existing Geo capability currently aggregates a Geo domain artifact with validated geographic primitives | Reference Geo concepts through public contracts; historical place identity, former names, boundaries and temporal geometry remain History concerns unless an existing contract proves otherwise |
| `document/pom.xml` | Existing Document capability describes document identity, metadata, versions, classification and links | Reuse document metadata and lifecycle rather than creating a parallel document aggregate |
| `documents/pom.xml` | Separate Documents capability provides provider-neutral document storage SPI and local/S3 adapters | Treat binary/object storage separately from historical source metadata and evidence |
| `core/framework/foundation/numou-foundation-kernel/pom.xml` | Foundation kernel owns clock/instant and other tiny universal primitives | Do not place BCE/CE chronology or historical calendar logic in the universal kernel |
| `core/framework/foundation/numou-foundation-domain/pom.xml` | Foundation domain already describes shared `DateRange`, `DomainClock`, Money, Quantity and other common primitives | Inspect DateRange semantics before reuse; it is not presumed to represent uncertain historical date expressions |

### 2.1 Audit limitations

This is a targeted POM and architecture-document audit, not a complete source-code call graph, API inventory, persistence-schema audit, or build verification. The GitHub code search index was unavailable for this repository during this pass. Before adding source dependencies or declaring an existing abstraction absent, M1 must inspect the actual Java sources, tests, migrations, and consumer usage for:

- Foundation date/time and DateRange classes;
- Event domain/application contracts and outbox semantics;
- Social Graph entity and relationship contracts;
- Geo domain value objects;
- Document metadata/version contracts and storage SPI;
- identity, tenancy, security, audit, search, media and workflow capabilities;
- existing timeline, history, calendar, genealogy, archive or chronology code, if any.

No Maven build or test is claimed as run by this audit.

## 3. Capability ownership and dependency rules

### 3.1 History owns

- Historical date expressions, calendar identity, chronology precision and uncertainty.
- Historical periods and periodization schemes.
- Historical event records and their classifications.
- Historical claims, interpretations, evidence links and citation attribution.
- Historical entity identity where a historical concept cannot safely be represented by an existing Party/Person/Organization contract.
- Temporal historical relationships and their provenance.
- Timeline definitions, perspective configuration and timeline query/projection semantics.
- Historical place identity, historical names, time-bounded geographic assertions and map-layer metadata, while reusing Geo primitives.

### 3.2 Existing capabilities remain authoritative

| Capability | Reuse boundary | History must not do |
|---|---|---|
| Foundation | Stable IDs, clock/instant, generic DateRange only where semantics match | Add BCE/CE or historical-calendar policy to the universal kernel |
| Event | Versioned integration event contracts, envelope, outbox/inbox and durable delivery as appropriate | Treat an integration event as the authoritative historical event record |
| Social Graph | Existing public graph contracts where relationship meaning and lifecycle match | Write Social Graph tables directly or force historical evidence semantics into a generic social edge |
| Geo | Validated geographic primitives and public spatial contracts | Reimplement coordinate validation or silently treat modern borders as timeless |
| Document | Document identity, metadata, versions, classification and links | Create a competing generic document lifecycle |
| Documents/storage | Object storage SPI and configured local/S3-compatible providers | Bind History domain code to RustFS, S3, filesystem or vendor SDKs |
| Identity/tenancy/security | Principal, tenant context, authorization and policy contracts | Treat a tenant ID or opaque identifier as authorization |
| Search/media/workflow | Existing public contracts after source-level verification | Introduce mandatory infrastructure dependencies into the domain |

Dependency direction: History domain remains pure and infrastructure-neutral. Application use cases depend on History domain and narrow public contracts. Persistence, search, media, Geo, graph, transport and ingestion adapters depend inward on the contracts they implement. Product applications and runtimes compose the selected artifacts.

### 3.3 Deployment profiles

The same History business rules must be usable in:
- an embedded/library composition;
- a standalone History application;
- a modular monolith;
- a distributed History service;
- cloud, private-cloud or on-premises installations.

A separate service, database, graph engine or search cluster is an operational choice, not a domain requirement.

## 4. Core model decisions

### 4.1 Historical chronology is not a Java timestamp

Canonical historical time must not be represented solely by `Instant`, `LocalDate`, or a Unix timestamp. Those types cannot express BCE dates, unknown precision, uncertain intervals, competing scholarly estimates, or non-Gregorian calendar expressions without loss.

M1 must design an immutable chronology model with at least:

- astronomical year numbering internally (including year zero);
- BCE/CE and BC/AD presentation policies;
- calendar identifier and calendar-specific interpretation;
- year/month/day/century/millennium precision;
- exact, approximate, bounded-uncertain, open-ended and unknown expressions;
- inclusive/exclusive interval semantics explicitly defined;
- original source expression retained verbatim;
- normalized comparison/sort bounds with uncertainty retained;
- support for Julian, Gregorian and extensible calendar adapters;
- conversion and formatting that never invent precision.

A historical date expression and its normalized comparison bounds are distinct. An approximate date such as `c. 500 BCE` must not become a falsely exact date. A date range must not be silently collapsed to its midpoint.

Astronomical year numbering is an internal arithmetic convention; user-facing BCE/CE and BC/AD formats must not expose an accidental year zero.

### 4.2 Separate three time axes

The model must distinguish:

1. **Historical valid time:** when the event, relationship or claim is asserted to have been true in history.
2. **Record time:** when Numou recorded or learned the information.
3. **Publication time:** when a version became publicly available.

A discovery dated to 1200 BCE, entered in 2026, and published in 2027 therefore has three separate temporal facts.

### 4.3 Facts, claims and interpretations

An event record must not collapse all historical assertions into one narrative string. Store independently attributable claims for occurrence, date, location, participants, cause, consequence and significance. Each claim may reference multiple sources and evidence items, confidence/assessment, competing claims, reviewer decision and revision history.

Confidence labels are editorial assessments, not mathematical truth scores. The source's own statement, the platform's assessment, and an AI-generated suggestion must remain distinguishable.

### 4.4 Timeline perspectives are projections

A timeline is a query/view definition over canonical History records. A perspective selects dimensions, filters, tracks, grouping, sort semantics, date window and display configuration.

Required initial perspective families:
- global and regional/geographic;
- civilization, culture, empire, kingdom and dynasty;
- person/biography and organization/institution;
- political, religious, military, economic and social;
- science, technology, culture, art and environment;
- comparative multi-track;
- user-defined/custom.

A perspective must not duplicate event records. Comparative views must preserve separate tracks and show contemporaneity without implying causal relationships. A timeline entry is a projection/reference to a source-owned historical record, not a second mutable event aggregate.

## 5. Proposed logical bounded contexts

These are logical boundaries; M1 must map them to the smallest suitable existing Maven artifact structure rather than create one artifact per noun.

| Context | Responsibility | Initial implementation posture |
|---|---|---|
| Chronology | Calendar/date expressions, precision, uncertainty, comparison and formatting | Dedicated History-owned domain package; not Foundation kernel |
| Historical Record | Events, periods, types, participants and lifecycle | History-owned aggregate and application ports |
| Historical Entities | People, civilizations, polities, dynasties, institutions, artifacts and places | Reuse existing identity/party/geo contracts only where semantic fit is demonstrated |
| Historical Relations | Typed, time-bounded relations and provenance | Relational authoritative model first; graph projection optional |
| Evidence and Sources | Bibliography, sources, claims, evidence, interpretation and review | History-owned provenance and claim model; Document references reused |
| Timeline Query | Perspectives, tracks, filters, temporal grouping and projection | Read/query model, separate from event write model |
| Historical Geography | Time-bounded place assertions, former names, territories, routes and map-layer metadata | Geo adapter/contract plus History-owned temporal assertions |
| Archive integration | Media/document references, provenance and licensing | Reuse Document and storage contracts; no binary storage in aggregates |
| Ingestion | External source adapters, normalization, entity resolution and import jobs | Optional adapters; no external source is authoritative by default |
| Publishing | Curated collections, exhibition and publication workflow | Compose existing Workspace/Document/Workflow capabilities after audit |

## 6. Persistence and integration baseline

- PostgreSQL is the initial authoritative persistence target, subject to existing repository persistence conventions.
- History owns its write schema and migrations; other capabilities do not write its tables.
- Historical entities and relations use stable IDs and explicit references, not cross-capability mutable entity instances.
- Typed relational relationship tables are the initial authoritative graph representation.
- Neo4j is optional and, if introduced, is a rebuildable projection; it is not a mandatory runtime dependency or second source of truth.
- Timeline read projections and search indexes are rebuildable from authoritative History records and versioned contracts.
- Object bytes remain in the selected Document/storage provider; History stores metadata and stable document/media references.
- Side-effecting ingestion and publication commands carry tenant, actor, correlation/causation and idempotency context where applicable.
- Use the existing Event outbox/inbox contracts after M1 verifies their semantics; do not invent a second generic outbox.
- Public/shared historical knowledge, institution-owned archives, tenant-private research and licensed media require distinct ownership and access policies.

## 7. Initial API and event contract direction

M1 should define versioned contracts after inspecting repository REST conventions. Candidate read resources:

- `GET /api/v1/history/timeline`
- `GET /api/v1/history/events`
- `GET /api/v1/history/events/{eventId}`
- `GET /api/v1/history/periods`
- `GET /api/v1/history/entities/{entityId}`
- `GET /api/v1/history/claims/{claimId}`
- `GET /api/v1/history/sources/{sourceId}`
- `GET /api/v1/history/timelines/{timelineId}`

Candidate commands include create/revise historical record, attach claim/evidence, submit for review, approve/reject publication, and ingest a source batch. These are vocabulary proposals, not existing endpoints or final DTO schemas.

Potential integration events include HistoricalRecordCreated, HistoricalRecordRevised, HistoricalClaimPublished, HistoricalSourceLinked and TimelinePublished. They must use existing platform event envelope/versioning/idempotency semantics and must not duplicate the authoritative event aggregate.

## 8. Security, tenancy and governance

- Every application boundary authorizes access to the requested resource; discovery, navigation visibility or identifier possession is not authorization.
- Public reference knowledge, tenant-owned private research, institutional collections and licensed media must have explicit ownership and visibility.
- Tenant-scoped writes and private reads require tenant context and isolation tests.
- Public shared records must not accidentally become tenant-owned or be duplicated per tenant.
- Source licensing, attribution, permitted use, takedown and retention metadata are mandatory for imported media and documents.
- Preserve claim revisions and review decisions; corrections supersede prior versions rather than erasing provenance.
- AI output remains an unverified suggestion until reviewed under an explicit publication policy.

## 9. M0 exit criteria and M1 work

M0 is complete when this architecture baseline is reviewed and the remaining source-level questions are resolved before code dependencies are introduced.

M1 must:
1. Inspect source and tests for Foundation DateRange/clock, Event contracts, Social Graph relationships, Geo primitives, Document metadata and storage, and tenancy/security/audit/search/media conventions.
2. Search the full repository for existing historical, calendar, chronology, timeline, date interval, period, biography and archive abstractions.
3. Produce a reuse-versus-build matrix with exact source paths and contract evidence.
4. Define chronology types and invariants, including BCE/CE arithmetic, interval semantics, calendar adapter SPI and uncertainty.
5. Define History module/artifact layout based on the existing reactor conventions and actual consumers.
6. Define PostgreSQL schema, indexes, migration ownership and tenant/public visibility model.
7. Define versioned application/API/event contracts and idempotency behavior.
8. Add chronology invariant and architecture tests in the first implementation slice.
9. Run the narrowest real Maven reactor and report exact commands, results and environment limitations.

## 10. Explicit non-goals for M0

- No speculative Java classes or empty Maven modules.
- No mandatory Neo4j, Elasticsearch, Kafka, RustFS or external provider.
- No direct writes to Social Graph, Document, Geo or Event persistence.
- No independent generic Event Bus or duplicate document/media lifecycle.
- No mass ingestion or unverified historical dataset.
- No timeline UI before query semantics and chronology contracts are stable.
- No claim of production readiness, compilation or tests without tool evidence.

## 11. M0 decision summary

Proceed with a History-owned chronology and historical knowledge domain; reuse platform contracts selectively; keep PostgreSQL authoritative; treat graph/search/timeline views as rebuildable projections; and make multiple timeline perspectives first-class query definitions over one canonical historical knowledge base. The next code milestone is M1 chronology, after the source-level reuse audit is complete.
