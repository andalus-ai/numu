# Public-Sector Population Platform — M0.3 Administration, Jurisdiction and Geography Contracts

**Status:** Architecture proposal  
**Milestone:** M0.3 — final architecture-design pass  
**Scope:** Public administration, agency authority, jurisdiction, administrative geography, authorization integration and event/API contracts  
**Implementation status:** Design only. No application code, database migrations, generated clients, or runtime policies are introduced by this document.

## 1. Purpose

Define the contracts required before implementing authoritative public-sector registries. The platform must support national, provincial, regional and local government; multiple agencies; shared services; delegated authority; historical administrative changes; and integrations with external authoritative sources.

The design must preserve Numou's reusable application-framework character. Government capabilities are bounded contexts that may be composed in a modular monolith or deployed independently. A bounded context is not automatically a separate service or Maven artifact.

## 2. Architectural decisions

1. **Do not create a third person or organization identity model.** Reuse canonical Party identity and the converged Organization capability described by the M0.1/M0.2 audits.
2. **Keep five concepts distinct:** Party, Organization, Tenant, Administrative Unit and Jurisdiction Assignment.
3. **Agency identity is not agency authority.** An Organization may be classified as a government agency, but its legal mandate and authority over a jurisdiction require explicit, effective-dated records.
4. **Administrative geography is a legal/administrative domain.** It owns official units, code namespaces, parent relationships and versioned boundary releases. It reuses Geo primitives and spatial infrastructure rather than redefining them.
5. **Authorization is purpose-aware and evidence-based.** Tenant membership or a caller-supplied agency/jurisdiction identifier never proves authority to access a registry record.
6. **Operational registries remain authoritative.** Analytics, statistical products and configurable workspace databases are consumers, not substitutes for civil, land, social-protection or justice registries.
7. **Historical facts are first-class.** Legal validity and system knowledge time must be represented separately where a decision depends on history.
8. **Jurisdiction and geography are not hard-coded to Indonesia's current administrative hierarchy.** Indonesia is the first localization profile; other legal systems must be representable without changing the neutral domain.

## 3. Core identity and scope vocabulary

| Concept | Meaning | Must not be treated as |
|---|---|---|
| PartyId | Canonical identity of a person or organization | Login ID, tenant ID, citizen number or authorization |
| PersonRef | Existing person reference used by consumers such as Workforce | An alias for PartyId unless an explicit mapping contract establishes it |
| OrganizationId | Identity of an organization and its managed structure | AdministrativeUnitId or TenantId |
| TenantId | Platform isolation and tenancy boundary | Government jurisdiction, legal agency mandate or data ownership |
| AdministrativeUnitId | Stable identity of a legally/administratively recognized geographic unit | A code, address, map tile or tenant |
| JurisdictionAssignmentId | A bounded, effective-dated grant/record of an agency's authority over a scope | Organization membership or reporting hierarchy |
| AgencyAppointmentId | A time-bounded appointment of a person to an office/role | Authentication role alone |
| DelegationId | A time-bounded delegation of specified powers by a competent authority | An unrestricted transfer of agency ownership |

All identifiers are opaque stable IDs. External codes are attributes with a namespace, issuer, validity period and provenance; they are not primary keys.

### 3.1 Party and Organization integration

- Party remains the canonical person/organization identity anchor.
- Person-specific attributes, civil status, household membership and registry relationships belong to their bounded contexts, not to a growing universal Party aggregate.
- Organization owns organizational structure, units and memberships. Government agency classification and public mandate are separate government-domain facts associated with an Organization.
- The Organization convergence work must explicitly define the persisted PartyId association and tenant participation semantics. Do not create a government-only organization aggregate to bypass that work.
- A PartyId-to-OrganizationId association must be explicit and validated. Never assume UUID equality or cast one identifier type into another.
- Preserve existing OrganizationId and PersonRef consumer contracts during migration. Introduce adapters and versioned compatibility paths rather than silently changing serialized identifiers.

## 4. Bounded contexts

### 4.1 Public Administration and Agency Authority

Owns:
- agency classification and official agency identifiers;
- agency mandate and legal basis references;
- agency-to-jurisdiction authority assignments;
- appointments to offices or authorized positions;
- delegation and revocation of specified powers;
- authority evidence and provenance.

Does not own:
- general organization structure, employees or authentication accounts;
- administrative boundary geometry;
- the underlying citizen, land, welfare or justice records.

An agency is represented by an existing Organization plus government-specific agency facts. A department, office or unit may map to an OrganizationUnit where that is the correct organizational concept. A geographic administrative unit is not an OrganizationUnit.

### 4.2 Administrative Geography

Owns:
- administrative-unit identity and classification;
- official code namespaces and code history;
- parent-child relationships and their validity periods;
- names and localized labels;
- versioned boundary releases and references to geometry;
- provenance, publication and retirement of boundary datasets.

Does not own:
- postal addresses or contact points;
- generic Geo coordinate primitives;
- arbitrary service-area polygons that have no legal administrative meaning;
- an agency's authority to act within a unit.

An AdministrativeUnit may exist without a corresponding government agency. A boundary may change while the legal unit's identity remains stable; a legal reorganization may instead retire units and create successor units. The source authority determines which transition applies.

### 4.3 Registry bounded contexts

Civil Registration, Household, Land and Property, Social Protection, Agriculture, Livestock, Fisheries, Public Safety and Justice, and other registries own their own records and domain rules.

They consume:
- PartyId and validated person/organization references;
- AdministrativeUnitId and historical geography facts;
- OrganizationId for responsible agencies where appropriate;
- trusted authority and authorization decisions.

They do not write another context's authoritative records directly. Cross-context changes use documented APIs/commands or versioned events.

### 4.4 Government Data Exchange and Official Statistics

Owns interoperability contracts, source registrations, data-sharing agreements, ingestion status, quality controls and analytical publication metadata.

Statistical products must preserve source, extraction/observation period, transformation version, coverage, limitations and disclosure controls. Analytical datasets must not silently become authoritative operational records.

## 5. Administrative geography domain contract

### 5.1 Administrative unit

Conceptual fields:

- administrativeUnitId
- classificationSchemeId
- unitTypeCode
- officialIdentifiers[] — issuer/namespace, code, valid-from/to, source
- names[] — language, value, valid-from/to
- status
- validTime — when the fact is legally effective
- recordedTime — when the platform recorded the fact
- sourceAuthority
- sourceReference
- revision

The exact persistence representation is deferred to implementation design. Do not flatten all history into a mutable current-name/current-parent row.

### 5.2 Classification schemes and hierarchy

A classification scheme defines allowed unit types and relationship rules. A scheme may describe national, provincial, municipal, district, village or other units, but the domain must not assume every country uses the same hierarchy or terminology.

Parent-child relationships are explicit, versioned and effective-dated. A unit may have different parents across time. The model must distinguish:
- legal parent/administrative hierarchy;
- statistical reporting aggregation;
- service delivery coverage;
- electoral or other special-purpose geography.

These relationships may overlap but are not interchangeable.

### 5.3 Codes and identifiers

- Codes are scoped by an issuing authority and namespace.
- Codes may be reused only where the issuer's rules allow it and where validity intervals do not create ambiguity.
- Renaming or recoding does not automatically create a new AdministrativeUnitId.
- A legal split, merger or abolition records predecessor/successor relationships and effective dates.
- Never use a code as a foreign key across contexts.

### 5.4 Boundary releases

A boundary release is an immutable published version with:
- release ID and classification/geography scope;
- issuing authority and source reference;
- publication and effective dates;
- dataset version, checksum and licensing/usage terms;
- coordinate reference system and geometry format;
- validation status and quality report;
- supersedes/superseded-by relationship where applicable.

Geometry is stored or referenced through the selected spatial adapter. The geography context owns the legal meaning and release lifecycle; Geo owns reusable coordinate, distance, bounds and spatial primitives. A grid cell or geohash is an index aid, never an administrative boundary.

Validation must cover geometry validity, coordinate reference system, topology constraints appropriate to the scheme, parent containment where legally required, and reproducible import/checksum evidence. Historical spatial queries must identify which boundary release and effective date were used.

## 6. Agency authority and jurisdiction

### 6.1 Separate relationship types

The platform must not infer authority from:
- Organization parent/child structure;
- employee reporting lines;
- TenantId;
- a user's role name;
- physical location or address;
- a caller-provided jurisdiction ID.

Represent separately:
1. **Organizational hierarchy** — who reports to or is structurally contained by whom.
2. **Legal mandate** — what an agency is legally empowered or required to do.
3. **Jurisdiction assignment** — where and over which subject matter that mandate applies.
4. **Appointment** — which person is appointed to an authorized office or role.
5. **Delegation** — which powers are delegated, by whom, to whom, under what constraints and period.
6. **Operational collaboration** — which agencies cooperate without transferring legal authority.

### 6.2 Jurisdiction assignment

Conceptual fields:
- jurisdictionAssignmentId
- agencyOrganizationId
- administrativeUnitId or another explicitly typed scope reference
- authorityType / mandateCode
- subjectMatter
- permittedActions
- legalBasisReference
- validFrom, validTo
- recordedFrom, recordedTo
- status
- sourceAuthority and sourceReference
- revision

The model must support one agency serving multiple units, multiple agencies having different powers in the same unit, national agencies with nationwide authority, and special-purpose jurisdiction that does not match the ordinary administrative hierarchy.

### 6.3 Appointment and delegation

Appointments must identify the appointee through a canonical person reference, the office/role, appointing authority, legal basis, effective period and revocation history.

Delegations must identify:
- delegating authority and recipient;
- exact actions and subject matters;
- scope and any geographic limits;
- legal basis and constraints;
- effective period and revocation;
- whether sub-delegation is permitted.

A delegation cannot grant powers the delegator does not possess. A revoked, expired or out-of-scope delegation must fail authorization. Appointment and delegation history must be retained for historical decision reconstruction.

## 7. Tenant and deployment model

TenantId is a technical isolation boundary, not a jurisdiction model. Support at least these deployment patterns without changing domain semantics:

- one national shared-service tenant serving many agencies;
- one tenant per agency with governed cross-agency exchange;
- a regional or local government tenant;
- a dedicated deployment for an agency;
- a multi-tenant platform where an agency participates in multiple service contexts.

A principal may act for more than one agency if separately authorized. A user may belong to a tenant without holding any government office. A government agency may have jurisdiction across multiple tenants or regions.

Cross-tenant access requires an explicit sharing/processing agreement and authorization policy. Do not bypass tenant isolation by treating national jurisdiction as a wildcard tenant. Data exchange must use controlled service contracts, scoped credentials and auditable purpose.

## 8. Purpose-aware authorization contract

### 8.1 Decision inputs

A policy decision must be based on trusted, resolved facts:

- authenticated principal and active session;
- tenant context and membership, where relevant;
- acting agency and validated OrganizationId;
- appointment and delegation evidence;
- action and resource type/identifier;
- resource owner and jurisdiction;
- data classification and field-level restrictions;
- declared purpose and lawful-basis reference;
- case/work-item reference where required;
- relevant effective time;
- consent or other legal condition where applicable;
- applicable sharing agreement and policy version.

Caller-supplied agency, purpose, jurisdiction or case IDs are claims to validate, not trusted evidence.

### 8.2 Decision sequence

1. Authenticate the principal.
2. Resolve tenant and verify tenant membership or service identity as required.
3. Resolve the acting agency through trusted Organization and agency records.
4. Verify appointment and any required delegation at the relevant effective time.
5. Resolve resource classification, owner, jurisdiction and applicable registry policy.
6. Validate the requested purpose and lawful basis against the operation and data class.
7. Apply minimum-necessary and field-level disclosure rules.
8. Evaluate sharing/consent/exception conditions where applicable.
9. Return a decision with policy version, reason code and obligations.
10. Record the access decision and required audit evidence.

Any missing, stale, conflicting or unverifiable authority evidence fails closed for protected operations. A policy engine evaluates facts; it must not become the source of truth for agency mandates, appointments, boundaries or registry ownership.

### 8.3 Decision result

Conceptual response:
- decision: permit / deny / permit-with-obligations;
- reasonCode;
- policyVersion;
- evaluatedAt;
- validUntil where a bounded decision is safe;
- obligations: masking, redaction, watermarking, no-export, case linkage, approval or audit requirements;
- evidenceReferences: identifiers of resolved authority facts, not sensitive payload copies.

Do not cache decisions beyond the validity of their underlying appointment, delegation, policy or resource facts. High-risk registry access may require fresh evaluation.

### 8.4 Classification and sensitive domains

Classification is attached to records and, where needed, individual fields or document sections. At minimum, define policy categories for public, internal, confidential and highly restricted information, with domain-specific refinements.

Health, fertility, children, household vulnerability, poverty assessments, land ownership, criminal allegations, convictions and justice case data require domain-specific legal controls. Criminal/justice information must never be represented as a public citizen flag or generalized risk score. Statistical release requires aggregation, disclosure review and suppression controls appropriate to the dataset.

This architecture does not determine local legal bases. Each deployment must configure reviewed legal-purpose catalogs, retention schedules, access policies and data-sharing agreements for its jurisdiction.

## 9. Temporal and provenance requirements

For facts whose legal validity changes over time, store both:
- **valid time:** when the fact is true in the real/legal world;
- **recorded time:** when the platform knew or recorded the fact.

This supports late-arriving corrections, retroactive legal changes and reconstruction of what the system knew at a prior time.

Corrections must preserve prior revisions and record actor, reason, source, correlation ID and approval where required. Avoid destructive updates to legally significant records. Event time alone is not sufficient provenance.

Every imported authoritative fact should identify source system/authority, source record reference, extraction or receipt time, source version where available, validation outcome and transformation version. External identifiers must be scoped by source and namespace.

## 10. API and port boundaries

The following are conceptual contracts, not a commitment to exact Java names or endpoint paths.

### Administrative Geography commands
- register administrative unit;
- update localized name or official code with validity;
- record parent relationship;
- publish or supersede boundary release;
- record unit split, merger, abolition or successor relationship.

### Administrative Geography queries
- resolve unit by stable ID;
- resolve official code by issuer/namespace and effective date;
- retrieve hierarchy as-of a date;
- retrieve boundary release for a date;
- resolve point-to-unit using a specified release and spatial policy.

### Agency Authority commands
- register/update agency classification and mandate;
- assign/revoke jurisdiction;
- appoint/revoke office holder;
- grant/revoke delegation.

### Agency Authority queries
- resolve agency authority for action, subject matter and scope as-of a date;
- resolve appointment/delegation evidence;
- enumerate an agency's current and historical jurisdiction assignments.

### Authorization port
Registry contexts call a shared authorization decision port with typed resource/action context. They do not query another context's tables or reimplement jurisdiction rules locally.

API contracts must be versioned. Commands must define idempotency behavior, expected revision/optimistic concurrency, validation errors and authorization requirements. Queries must declare tenant and jurisdiction scope explicitly where applicable.

## 11. Events and integration

Publish versioned, minimal events through the platform's transactional outbox/inbox conventions. Candidate event families:

- AdministrativeUnitRegistered
- AdministrativeUnitClassificationChanged
- AdministrativeUnitCodeChanged
- AdministrativeHierarchyChanged
- BoundaryReleasePublished
- BoundaryReleaseSuperseded
- AgencyMandateChanged
- AgencyJurisdictionAssigned
- AgencyJurisdictionRevoked
- AgencyAppointmentGranted
- AgencyAppointmentRevoked
- AuthorityDelegationGranted
- AuthorityDelegationRevoked

Common envelope fields should include event ID, event type/version, occurred time, recorded time, producer, tenant context where applicable, actor/service identity, correlation ID, causation ID and aggregate revision.

Events should carry stable references and the minimum data needed by consumers. Do not publish personal data, case details, land-owner details or other restricted payloads merely for convenience. Consumers must be idempotent, tolerate duplicates and out-of-order delivery, and support replay/reconciliation. A consumer's projection is not an authoritative source.

## 12. Indonesia-first localization profile

The first deployment profile should be able to represent the Indonesian national/provincial/regency-or-city/district/village hierarchy and official code systems, including historical changes. These are configuration and source-adapter concerns, not hard-coded assumptions in the neutral domain.

The integration inventory should evaluate authoritative sources such as Dukcapil for civil registration/population, BPS for official statistics and statistical classifications, ATR/BPN for land administration, and relevant ministries/agencies for sectoral datasets. The platform must not imply that it is itself the legal source of truth for those records.

For each integration, document:
- source authority and legal basis;
- permitted data categories and purposes;
- official identifier namespace;
- API/file/exchange protocol and version;
- synchronization and correction semantics;
- source-of-truth ownership by field;
- provenance and quality checks;
- data-sharing, retention and audit obligations.

Do not infer that a BPS statistical code is interchangeable with a civil-administration code or a cadastral identifier. Maintain explicit crosswalks with source, validity period and confidence/validation status.

## 13. Operational and analytical separation

Operational registries own transactions and legally meaningful records. Analytical pipelines consume governed extracts/events and publish aggregates, indicators and statistical products.

Examples:
- poverty is a dated assessment/eligibility history in Social Protection, not a permanent Person attribute;
- birth and death registrations belong to Civil Registration;
- household membership is a temporal relationship in Household Registry;
- land rights and parcels belong to Land Registry and are not Person attributes;
- agricultural production observations belong to the relevant agricultural/fisheries/industry context;
- fertility and health information remains in restricted health/statistical contexts with lawful purpose and disclosure controls;
- justice events and dispositions remain in restricted justice contexts.

A lakehouse/warehouse may be external. Numou should define interoperable data contracts, metadata and governed exchange rather than force a particular analytical storage engine.

## 14. Reliability, integrity and security invariants

- Tenant isolation is enforced independently of jurisdiction checks.
- Authorization uses trusted authority evidence and fails closed for protected data.
- No identifier type is silently interchangeable with another.
- Effective-dated authority, appointment, delegation, code and hierarchy changes preserve history.
- Boundary releases are immutable after publication; corrections create a new release.
- Commands are idempotent or have explicit duplicate semantics.
- Writes use optimistic concurrency and transactional outbox where events are emitted.
- Consumers use inbox/deduplication and replay-safe handlers.
- Cross-context writes are prohibited; use APIs/commands/events.
- Sensitive record access is auditable, including actor, acting agency, purpose, resource, decision, policy version, time and correlation/case reference as required.
- Logs and events avoid sensitive payloads and secrets.
- Retention, correction, legal hold, archival and deletion behavior are defined per domain and applicable law; no generic hard-delete policy overrides statutory obligations.
- Schema and event compatibility are tested in CI, including old consumer compatibility and migration/replay behavior.

## 15. Failure behavior

Protected operations must deny by default when:
- tenant context is missing or inconsistent;
- agency identity cannot be resolved;
- appointment or delegation is expired, revoked or unavailable;
- jurisdiction assignment is absent or ambiguous;
- legal purpose is missing or invalid;
- resource classification or ownership cannot be resolved;
- required sharing agreement or approval is unavailable;
- the relevant boundary/authority revision cannot be determined.

Return stable, non-revealing error codes. Do not disclose whether a protected citizen, case, parcel or record exists when the caller is not authorized to know that fact.

For non-authoritative analytics, stale or incomplete data may be shown only with explicit freshness, source and coverage metadata. Never silently present stale projections as current authoritative registry facts.

## 16. M1 implementation gates and sequence

M1 must not begin by creating a Citizen aggregate or adding government fields to Party. Recommended sequence:

1. **Organization convergence plan:** confirm PartyId association, tenant participation, migration strategy and production persistence requirements without breaking OrganizationId consumers.
2. **Administrative Geography domain contract:** settle stable IDs, classification schemes, official code namespaces, bitemporal hierarchy and boundary release lifecycle.
3. **Agency Authority contract:** settle mandate, jurisdiction assignment, appointment and delegation invariants.
4. **Authorization integration contract:** map existing identity, tenant guard, ABAC and audit facilities; identify missing trusted evidence resolvers and purpose controls.
5. **Persistence and migration design:** select database/spatial adapter and migration strategy only after domain contracts and source ownership are settled.
6. **Vertical slice:** implement one low-risk administrative geography workflow with provenance, history, tenant isolation, authorization and integration tests before adding citizen or sensitive registry data.

### M1 exit criteria

- No third Party/Person/Organization identity model is introduced.
- Existing PersonRef, PartyId and OrganizationId consumers have an explicit compatibility and migration plan.
- Administrative geography can represent Indonesia's initial hierarchy and historical changes without hard-coding it into shared core logic.
- Agency mandate, jurisdiction, appointment and delegation are separate and queryable as-of a time.
- Authorization decisions require validated authority and legal purpose.
- Operational registry ownership and analytical data contracts are explicit.
- Threat model, data classification, audit, retention and cross-agency sharing are reviewed.
- API/event schemas, idempotency, migration tests and tenant/jurisdiction isolation tests are specified.

## 17. Open decisions requiring evidence before implementation

The following are intentionally not guessed in this design:
- Which existing Organization persistence adapter is the canonical production path and how the PartyId association is migrated.
- Whether administrative geography requires PostGIS in the first deployment or can initially use a pluggable spatial adapter with a constrained first use case.
- Which official source and version is authoritative for each Indonesian code, boundary and registry field.
- Which legal-purpose catalog, retention schedules and data-sharing agreements apply to each deployment.
- Which domains require separate data residency, encryption-key, approval or operational boundaries beyond ordinary tenant isolation.

These decisions require repository/source inspection, deployment requirements and legal/authority evidence. They must not be filled with assumed framework defaults.

## 18. Scope and verification statement

This document is an architecture contract proposal only. It does not claim that the described domains, APIs, persistence adapters, policy resolvers, spatial capabilities or integrations already exist. No application tests or builds are implied by this documentation change.

Related documents:
- PUBLIC-SECTOR-POPULATION-PLATFORM-M0-AUDIT.md
- PUBLIC-SECTOR-POPULATION-PLATFORM-M0.1-CAPABILITY-TRACE.md
- PUBLIC-SECTOR-POPULATION-PLATFORM-M0.2-IDENTIFIER-CONSUMER-INVENTORY.md
