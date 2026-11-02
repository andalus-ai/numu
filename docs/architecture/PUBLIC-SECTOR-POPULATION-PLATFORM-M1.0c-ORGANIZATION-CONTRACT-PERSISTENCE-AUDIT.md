# Public-Sector Platform — M1.0c Organization Contract and Persistence Evidence Audit

**Status:** Source-level implementation-readiness audit (partial; limitations explicit)  
**Repository:** `andalus-ai/numu`  
**Branch:** `feat/public-sector-platform-m0-audit`  
**Inspected HEAD:** `6532440556e8178bfc4dcf13b56adf208d6d765e`  
**Scope:** Verify Organization, Party, Tenant and selected downstream reference contracts, persistence adapters and tenant semantics before designing M1.0 implementation.  
**Implementation status:** Documentation only. No application code, schema, migration, API, event or data changed.

## 1. Executive result

M1.0b's conservative direction is supported by the current source:

- Party is the canonical cross-domain identity for PERSON and ORGANIZATION.
- Ecosystem Organization is a Party-keyed organization profile.
- Top-level Organization is a distinct operational aggregate with its own OrganizationId, mandatory TenantId, units and UserId-based application memberships.
- Tenancy's OrganizationRef and Workforce's OrganizationRef are UUID wrappers with different documented semantics; neither proves a PartyId or OrganizationId mapping.
- Courier explicitly requires tenant-scoped Organization lookup using both TenantId and OrganizationId.
- Construction now persists a requesting-organization UUID in a sensitive evidence-operation context, but the migration does not identify the UUID's source contract.
- In the inspected tree, the top-level Organization capability has in-memory repository adapters only; Tenancy has in-memory adapters only; and no dedicated JDBC persistence implementation/migration was found for the Ecosystem Organization profile.

**M1.0c conclusion:** do not introduce an OrganizationId↔PartyId association table, rewrite OrganizationRef, change Tenant.organization, or start a production registry on the basis of the current evidence alone. First clarify the public contract semantics and persistence ownership, then prepare an implementation plan with explicit compatibility behavior.

## 2. Evidence method and limits

The recursive Git tree was fetched at the inspected HEAD and reported 13,245 entries with `truncated=false`. Exact source files were then fetched for:
- Top-level Organization aggregate, refs, commands and handlers.
- Organization membership and repository adapters.
- Foundation OrganizationRef.
- Tenant aggregate, creation use case, membership and TenantOrganizationChanged event.
- Workforce OrganizationRef.
- Ecosystem Organization aggregate, registration handler and repository port.
- Courier OrganizationLookup.
- Construction consultancy tenant context and the evidence requester-organization migration.

This is a targeted source-level trace, not a complete repository-wide call graph. It does not establish:
- Production database contents or external customer data.
- Runtime CDI/provider registration and actual deployment composition.
- Every API DTO, generated SDK, event consumer, serialized payload or external integration.
- All uses of generic UUID fields or wrapper conversions.
- Whether the inspected branch has since been deployed.
- Whether uninspected modules contain additional Organization persistence or adapters.

GitHub code-search indexing has previously returned zero results for important identifiers and is not treated as proof of absence. No live database or production runtime was inspected.

## 3. Canonical Party and Ecosystem Organization

### 3.1 Party identity

Previously inspected M0.1/M0.2/M0.4 evidence establishes:
- PartyId is a strongly typed UUID identity.
- Party distinguishes PERSON and ORGANIZATION and has its own ACTIVE/INACTIVE lifecycle.
- Party is global, not tenant-owned.
- Party identity associations and participant associations do not grant application access or government authority.

Party remains the correct canonical identity reference for cross-domain person/organization identity. It is not a substitute for tenant membership or operational organization structure.

### 3.2 Ecosystem Organization profile

Verified source:
- `core/framework/ecosystem/ecosystem-domain/.../organization/Organization.java`
- `core/framework/ecosystem/ecosystem-application/.../RegisterOrganizationHandler.java`
- `core/framework/ecosystem/ecosystem-application/.../port/OrganizationRepository.java`

Confirmed behavior:
- Aggregate identity is PartyId.
- Registration handler loads Party in the Ecosystem transaction and rejects missing Party, non-ORGANIZATION kind, inactive Party, or an already registered profile.
- Profile stores legalName, displayName, OrganizationType, OrganizationStatus and optional tenant UUID.
- Repository port is `findByPartyId(PartyId)`.
- Handler saves the profile and publishes its domain events within the Ecosystem transaction boundary.
- Profile comments explicitly describe organization-specific classification, names, tenant association and lifecycle.

Persistence finding:
- No dedicated JDBC repository implementation or migration for this Organization profile was found in the inspected source tree. The port and transaction abstraction are confirmed; production provider wiring and durable storage are not established by this audit.

Domain implication:
- This is not a second canonical identity key. It is a Party-attached profile.
- Its status is not automatically equivalent to Party status or top-level Organization status.
- Its optional tenant UUID is not evidence of tenant authorization or a complete tenant ownership model.

## 4. Top-level Organization capability

Verified source:
- `organization/organization-domain/.../Organization.java`
- `OrganizationId.java`, `LegalEntityRef.java`, `BusinessUnitRef.java`
- `CreateOrganizationHandler.java`, `InviteOrganizationMemberHandler.java`
- Organization repository SPI and memory adapters.

Confirmed behavior and semantics:
- Organization has its own OrganizationId and requires TenantId at construction.
- Fields include name, legalName, registrationNumber and OrganizationStatus.
- Class Javadoc says it represents a legal entity or business unit.
- A separate `LegalEntityRef` wraps OrganizationId, and `BusinessUnitRef` pairs OrganizationUnitId with OrganizationId.
- OrganizationUnit is a child/structural concept with its own identifier.
- CreateOrganizationHandler creates an Organization from command TenantId and profile fields, then calls OrganizationRepository.save.
- InviteOrganizationMemberHandler loads by OrganizationId, checks that the aggregate TenantId equals the command TenantId, rejects inactive Organizations, validates the Identity user exists, and rejects duplicate membership.
- OrganizationMembership is explicitly OrganizationId + UserId and has PENDING/ACTIVE/SUSPENDED/REVOKED states.

Persistence finding:
- The inspected Organization adapter consists of in-memory Organization, membership and unit repositories.
- The recursive tree contained the Organization domain/SPI/application/adapter and tests, but no PostgreSQL/JDBC adapter or migration directory under the top-level Organization capability.

Important security nuance:
- InviteOrganizationMemberHandler does enforce an organization/tenant equality check, but the handler itself does not show authentication, actor authorization or an agency authority decision. Those must be supplied and verified at the authoritative application/runtime boundary; possession of OrganizationId and TenantId must not be treated as sufficient permission.
- The current lookup API is by OrganizationId and the handler applies tenant comparison after loading. This differs from Courier's stronger query contract (see section 7). A future production repository should support tenant-predicated lookup where feasible, rather than globally fetching and filtering.

Domain ambiguity:
- The aggregate's Javadoc permits both legal entity and business unit, while the model has a LegalEntityRef and OrganizationUnit. This is not a sufficiently precise type contract to infer whether an instance is a legal Party, an operating entity, or a tenant-local organizational construct.
- The mandatory TenantId's meaning is not proven to be creator, owner, partition or exclusive membership.

## 5. Tenant aggregate and Organization reference

Verified source:
- `foundation-domain/.../ref/OrganizationRef.java`
- `tenancy-domain/.../tenant/Tenant.java`
- `tenancy-domain/.../tenant/event/TenantOrganizationChanged.java`
- `tenancy-application/.../CreateTenantUseCase.java`
- `tenancy-domain/.../membership/TenantMembership.java`

Confirmed behavior:
- Foundation OrganizationRef is a UUID wrapper documented as a universal lightweight cross-domain Organization reference. Its Javadoc says Tenancy stores it instead of importing the full Organization aggregate.
- Tenant stores an optional OrganizationRef and its Javadoc calls it the legal Organization for the Tenant.
- Tenant.create accepts a nullable OrganizationRef; changeOrganization can set or clear it and emits TenantOrganizationChanged.
- CreateTenantUseCase passes the command's OrganizationRef into Tenant.create. The inspected use case does not validate that the referenced Organization exists or that it is a Party.
- TenantMembership is a distinct aggregate over TenantId and UserRef. Its Javadoc explicitly distinguishes tenant membership from employment and authorization.

Unresolved contract:
- OrganizationRef contains only UUID. It does not encode whether its target is OrganizationId, PartyId or another legal-entity registry identifier.
- The Foundation Javadoc and Tenant Javadoc suggest a legal-entity target, but there is no verified adapter mapping from OrganizationRef to PartyId or top-level OrganizationId in this audit.
- No inference from equal UUID values is safe.

Persistence finding:
- The inspected Tenancy adapter tree contains in-memory Tenant, TenantMembership, provisioning and settings repositories. No Tenancy JDBC/PostgreSQL persistence adapter or migration was found in the inspected subtree.
- This is an inspected-tree finding, not a proof that no external persistence exists in another composition or deployment.

## 6. Membership semantics

Three distinct contracts are now directly supported by source:

| Contract | Identifier | Source meaning |
|---|---|---|
| Tenant membership | TenantId + UserRef | User's membership in a tenant |
| Organization application membership | OrganizationId + UserId | User's application membership in an operational Organization |
| Workforce employment | Worker/person and employment organization references | Employment context; not login or tenant membership |

Neither membership is evidence of:
- Real-world person affiliation unless a separate explicit employment/affiliation record says so.
- Agency appointment or delegated public authority.
- Permission to read all data owned by an organization.

Keep the existing UserId-based OrganizationMembership contract intact. Do not convert it to PartyId or PersonRef.

## 7. Downstream Organization references

### 7.1 Courier

Verified source:
`logistics/numou-courier-application/.../port/OrganizationLookup.java`

The contract requires both TenantId and OrganizationId. Its Javadoc explicitly states that implementations must predicate the repository query on both IDs; a global lookup followed by in-memory filtering is not an acceptable boundary.

Implication:
- This is a confirmed tenant-scoped consumer of the top-level OrganizationId.
- Preserve this contract and tenant-predicated lookup behavior.
- A future Organization persistence adapter must support the same isolation guarantee.

### 7.2 Workforce

Verified source:
`workforce/workforce-domain/.../employment/OrganizationRef.java`

This is a separate UUID wrapper documented as a reference to an external Organization in the organization/party domain. It implements DomainId<UUID> but does not encode PartyId or OrganizationId in its type.

Implication:
- Workforce's semantic target needs an explicit contract decision and consumer trace.
- Do not equate its UUID to either PartyId or OrganizationId without a verified mapping and compatibility migration.
- Existing Worker PersonRef usage remains a separate person-identity track.

### 7.3 Construction evidence requester

Verified migration:
`construction/construction-adapter/src/main/resources/db/migration/V20261030_02__evidence_requester_organization.sql`

The migration adds `requesting_organization_id UUID` to `construction_consultancy_evidence_operation`, then fails if any existing row remains null before making the column NOT NULL.

Its comment says the worker must retain the organization context authorized when evidence attachment was requested and must not infer it from a document UUID.

Implications:
- This is a security-sensitive, persisted organization context.
- The migration deliberately requires an explicit backfill; it does not guess.
- The migration alone does not prove whether the value is OrganizationId, PartyId or a different reference. The command, producer, worker and authorization decision must be traced before mapping it.
- Do not rewrite the column or its payload until that complete path is known.

### 7.4 Foundation LegalEntityRef and BusinessUnitRef

Top-level Organization exposes:
- LegalEntityRef(OrganizationId)
- BusinessUnitRef(OrganizationUnitId, OrganizationId)

These are evidence that OrganizationId is intended to support legal-entity and business-unit references in some domain contracts. They do not establish that OrganizationId equals canonical PartyId or that all consumers use it consistently.

## 8. Tenant ownership finding

The current source does not justify choosing among these meanings for Organization.TenantId:
- Creator tenant.
- Owning tenant.
- Data partition.
- Exclusive operating tenant.

The Organization aggregate requires TenantId; Courier enforces tenant-predicated lookup; InviteOrganizationMemberHandler checks tenant equality; Construction has tenant-context safeguards. These establish that tenant isolation is a real operational constraint, but not the exact legal/ownership meaning of the Organization aggregate's TenantId.

Recommended compatibility rule:
- Preserve current TenantId field and all current checks.
- Do not turn Organization into a globally readable aggregate.
- Do not infer government jurisdiction from TenantId or Organization membership.
- Before supporting shared/national organizations, define explicit tenant participation and authorization, with tenant-scoped records remaining isolated.
- Keep Tenant.organization (the tenant's legal-organization reference) conceptually separate from Organization.tenantId (the tenant context owning/partitioning the operational aggregate) unless product semantics explicitly prove they are the same relationship.

## 9. Persistence readiness matrix

| Context | Verified contract | Persistence found in inspected tree | Readiness conclusion |
|---|---|---|---|
| Party | PartyId, kind, lifecycle, repository | JDBC persistence and migrations previously confirmed | Existing identity foundation; authorization still required at each use |
| Ecosystem Organization | PartyId-keyed profile repository port | No dedicated JDBC adapter/migration found | Runtime/durable provider must be verified |
| Top-level Organization | OrganizationId, TenantId, units, UserId membership | In-memory repositories only | Not ready to serve as a production government agency/organization registry |
| Tenancy | Tenant, membership, settings, provisioning ports | In-memory repositories only | Do not assume durable tenant data from domain/API presence |
| Courier Organization lookup | TenantId + OrganizationId | Consumer port verified | Preserve strict tenant predicate |
| Workforce OrganizationRef | UUID wrapper | Consumer persistence not fully traced here | Semantic mapping unresolved |
| Construction requester organization | Raw UUID persisted as NOT NULL | Migration verified | Source identifier contract and backfill provenance unresolved |

A memory adapter may be appropriate for tests or a composition profile; it is not evidence of production persistence.

## 10. Risks and implementation prohibitions

Until the remaining contracts are verified:
1. Do not equate OrganizationId, PartyId, Foundation OrganizationRef, Workforce OrganizationRef or arbitrary organization UUID columns.
2. Do not automatically create a Party for every top-level Organization.
3. Do not automatically create a top-level Organization for every Organization Party.
4. Do not merge or synchronize overlapping legal/display names and lifecycle fields.
5. Do not change Tenant.organization or Organization.tenantId semantics.
6. Do not relax Courier's TenantId + OrganizationId lookup boundary.
7. Do not reinterpret UserId membership as employment, affiliation or public office.
8. Do not backfill Construction requester IDs from document IDs or guessed organization records.
9. Do not begin registry migrations before durable persistence, authorization, audit, transaction and rollback contracts are designed.

## 11. Required next evidence pass

### M1.0d — Complete reference and runtime trace

1. Trace all producer/consumer paths for Foundation OrganizationRef and TenantOrganizationChanged, including serializers, event bus and handlers.
2. Trace Workforce OrganizationRef from employment command through repository, JDBC entity, migration and all upstream callers.
3. Trace Construction evidence requester ID from authorized request command through persistence, worker execution and access checks; identify the source identifier and explicit backfill owner.
4. Inspect all OrganizationId/PartyId/OrganizationRef/LegalEntityRef/BusinessUnitRef usages in API DTOs, events, generated clients, fixtures and external-facing contracts.
5. Verify actual CDI/runtime provider wiring for Ecosystem Organization and top-level Organization, plus all persistence adapters across reactor roots.
6. Determine whether tenancy persistence exists outside the inspected Tenancy subtree or is intentionally in-memory in current supported compositions.
7. Document the compatibility contract and exact tenant-predicate requirement for each consumer.

### M1.0e — Code-level implementation proposal

Only after M1.0d, prepare a coherent implementation proposal for:
- Production Organization persistence (if confirmed missing for supported compositions).
- Production Tenancy persistence (if confirmed missing for supported compositions).
- Ecosystem Organization profile persistence/provider wiring (if confirmed missing).
- Explicit, optional Party association only if a demonstrated use case needs it.
- Tenant-aware authorization and audit at organization administration boundaries.
- Migration, rollback, idempotency, optimistic concurrency, outbox and integration test strategy.

Do not create speculative adapters for all persistence technologies. Select one supported reference composition (for example PostgreSQL) while keeping domain contracts deployment-neutral.

## 12. M1.0c acceptance criteria

- Source distinctions are documented with exact owning contracts.
- Tenant membership, Organization access membership, employment and agency appointment remain separate.
- Tenant isolation is preserved and Courier's strict lookup contract is retained.
- OrganizationId/PartyId/reference wrappers are not conflated.
- Missing persistence/provider evidence is explicitly recorded.
- Construction requester-organization identifier remains unmapped until its source is traced.
- The next milestone is targeted consumer/runtime trace, not identifier rewrite or speculative implementation.
