# Public-Sector Platform — M1.0 Organization Convergence Implementation Plan

**Status:** Implementation plan / design gate  
**Repository:** `andalus-ai/numu`  
**Branch:** `feat/public-sector-platform-m0-audit`  
**Scope:** Reconcile canonical Party identity with the existing Organization capability without breaking identifiers, tenant isolation, memberships or consumers.  
**Implementation status:** Planning only. No application code, schema, migration or API contract is changed by this document.

## 1. Decision summary

The repository has a canonical Party aggregate and a separate Organization capability. The Organization capability owns organizational structure, units and user memberships, but its current aggregate is tenant-bound and its inspected repositories are in-memory. Ecosystem also has an Organization profile keyed by PartyId. This is a convergence problem, not a reason to add another organization model.

**Target direction:**
1. Party remains the canonical cross-domain identity for a person or organization.
2. The top-level `organization/` bounded context remains the owner of organization profile, structure, units, affiliations and organization lifecycle.
3. The top-level `OrganizationId` remains the stable domain identifier for the Organization aggregate and its existing consumers.
4. Each Organization that represents a canonical organization Party has an explicit, validated `OrganizationId ↔ PartyId` association. Never infer the association from equal UUID values.
5. Tenant ownership/participation is modelled independently from canonical identity.
6. Existing UserId-based OrganizationMembership is retained as an access/membership relation during compatibility migration. Person-to-organization affiliation is a distinct PartyId-based relation.
7. The Ecosystem Organization profile must not grow a second structure, membership, unit or corporate hierarchy model. Its profile responsibilities should be reconciled into the Organization owner through a staged migration.
8. No destructive backfill, identifier replacement, consumer cutover or PR is authorized by this plan.

## 2. Evidence baseline

The following source files were inspected on the planning branch:

- `organization/organization-domain/.../Organization.java`
- `organization/organization-domain/.../OrganizationId.java`
- `organization/organization-domain/.../OrganizationMembership.java`
- `organization/organization-domain/.../OrganizationUnit.java`
- `organization/organization-spi/.../OrganizationRepository.java`
- `organization/organization-spi/.../OrganizationMembershipRepository.java`
- `organization/organization-spi/.../OrganizationUnitRepository.java`
- `organization/organization-application/.../CreateOrganizationHandler.java`
- `organization/organization-adapter/.../InMemoryOrganizationRepository.java`
- `core/framework/ecosystem/ecosystem-domain/.../Party.java`
- `core/framework/ecosystem/ecosystem-domain/.../PartyId.java`
- `core/framework/ecosystem/ecosystem-domain/.../PartyIdentityAssociation.java`
- `core/framework/ecosystem/ecosystem-domain/.../PartyParticipantAssociation.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../V1__party_identity_associations.sql`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../V2__canonical_parties.sql`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../V3__party_participant_associations.sql`
- `docs/architecture/organization-party-identity-convergence.md`

### 2.1 Confirmed facts

- `OrganizationId` and `PartyId` are distinct strongly typed wrappers over UUID.
- `Organization` currently requires a `TenantId`, and owns name, legal name, registration number and lifecycle.
- `OrganizationUnit` is an organizational subdivision and references `OrganizationId`.
- `OrganizationMembership` references `UserId`, not `PartyId`.
- The inspected Organization repository adapters are in-memory. The SPI repository ports are asynchronous.
- `Party` is the canonical cross-domain identity with PERSON/ORGANIZATION kind and lifecycle; it intentionally does not own profiles, contacts or organization structure.
- PartyIdentityAssociation links an external authenticated subject to Party within a tenant; it explicitly does not grant organization membership or authorization.
- PartyParticipantAssociation links Party to an Ecosystem Participant within a tenant; its roles are business classifications, not login membership or authorization.
- The two Organization representations must not be treated as interchangeable without a persisted association and migration evidence.

### 2.2 Evidence still required before implementation

This planning pass is not a complete consumer or data inventory. Before any schema migration, inspect:
- all OrganizationId/PartyId/OrganizationRef/PersonRef/UserId references across modules, generated clients, API DTOs, event schemas and serialized payloads;
- every existing Organization and Ecosystem Organization persistence table, migration, seed and fixture;
- all call sites of OrganizationRepository, OrganizationMembershipRepository and OrganizationUnitRepository;
- any production data and external integrations that use OrganizationId or PartyId;
- tenant creation, tenant ownership and cross-tenant organization participation flows;
- Organization aggregate rehydration, event publishing, transaction and optimistic-concurrency behavior.

A GitHub code-search result or a path-only tree is not sufficient evidence that all consumers have been found.

## 3. Target ownership and domain model

### 3.1 Party identity

Party owns:
- stable canonical PartyId;
- PERSON or ORGANIZATION kind;
- identity lifecycle.

Party does not own:
- OrganizationId;
- tenant ownership;
- organization units or membership;
- legal registration records;
- agency mandate or jurisdiction;
- user credentials or authentication roles.

The Organization capability validates Party kind through an application port. The Organization domain must not depend directly on an Ecosystem JDBC adapter or Identity implementation.

### 3.2 Organization aggregate

The top-level Organization capability owns:
- OrganizationId and organization profile;
- legal/display names and lifecycle;
- organization units and internal structure;
- organization-specific identifiers/registrations with issuer and jurisdiction semantics;
- person affiliations and organization relationships;
- explicit canonical Party association;
- tenant participation relationships or integration with the tenancy capability.

OrganizationId is retained as the Organization aggregate identity. Consumers should migrate to canonical Party references only where cross-domain identity semantics are required; internal organization structure continues to use OrganizationId.

### 3.3 Explicit Organization-to-Party association

Introduce a dedicated association owned by the Organization capability (exact Java package and adapter are implementation decisions).

Conceptual record:
- associationId;
- organizationId;
- partyId;
- association status;
- validFrom / validTo;
- recordedAt / recordedBy;
- source and verification evidence reference;
- revision;
- optional supersedes/replacement reference for corrected mappings.

Invariants:
- Party must exist and have kind ORGANIZATION before association activation.
- One active canonical Party association per Organization.
- One active Organization association per Party unless an explicitly reviewed legal model proves a valid one-to-many/many-to-one case. Default is one-to-one.
- Association is never inferred from equal UUID values.
- Association changes are audited and historically traceable; corrections do not erase previous evidence.
- Cross-tenant use of a Party association is still subject to authorization and tenant policy.
- The mapping does not itself grant tenant membership, user access, agency authority or legal control.

The association is an Organization-owned relationship because Organization owns the aggregate and its migration lifecycle. Party remains unaware of OrganizationId, avoiding a reverse dependency and dual ownership.

### 3.4 Membership must be split by meaning

The current `OrganizationMembership` stores `UserId`. This is an access/account relationship and must not be silently reinterpreted as a real-world person affiliation.

Keep distinct concepts:

| Relationship | Identifier | Meaning | Owner / integration |
|---|---|---|---|
| Authenticated account | UserId | Login principal | Identity |
| Account access membership | UserId + OrganizationId + tenant/context | User is allowed to act within an organization's application context | Organization access membership, integrated with Identity and Security |
| Person affiliation | PartyId + OrganizationId | Person is an employee, officer, member, contractor or other affiliate | Organization |
| Party-to-login linkage | PartyId + external identity subject | Authenticated subject represents a Party | Existing Ecosystem PartyIdentityAssociation |
| Tenant membership | UserId/service principal + TenantId | Principal may operate in a tenant | Identity/Tenancy |
| Agency appointment | PartyId + office + mandate + period | Person is appointed to a public office | Future Public Administration / Agency Authority |

Do not assume every employee has a login, every login user is an employee, or every organization affiliate is authorized to access the system.

**Compatibility rule:** retain the existing UserId membership contract as a legacy/access-membership concept until call sites and data have been migrated. Introduce Party-based affiliation as a separate contract. A later rename or replacement requires a deprecation window and consumer migration, not a silent field-type change.

## 4. Tenant ownership and participation

The current Organization aggregate has a mandatory TenantId. That is too restrictive as the long-term identity model for an organization that can interact with multiple tenants, but removing it immediately would risk breaking current callers and data ownership.

Target model separates:

1. **Canonical identity:** PartyId, global.
2. **Organization identity and structure:** OrganizationId, global within the Organization capability.
3. **Tenant participation:** an explicit relationship between TenantId and OrganizationId, with role/status/effective period and policy.
4. **Tenant data ownership:** which tenant owns a particular tenant-scoped record or workspace.
5. **Agency jurisdiction:** legal authority over an administrative scope, owned by Public Administration, never inferred from tenant participation.

Recommended staged approach:
- Preserve current tenantId semantics and existing API behavior during the compatibility phase.
- Determine whether it currently means creator/owner tenant, data partition, or the only permitted tenant. Do not guess.
- Introduce a separately named tenant participation/ownership relationship only after that semantic audit.
- Do not make Party tenant-scoped.
- Do not allow a second tenant to see the Organization's first tenant's records merely because it references the same OrganizationId.
- Enforce tenant isolation at the record/query boundary, independently of Organization-to-Party association.
- Support shared or national agencies through explicit tenant participation and governed data exchange, not wildcard tenant access.

Before deciding whether Organization aggregate itself becomes tenant-neutral, inventory how every organization-owned child and application query is partitioned. The implementation must avoid a global aggregate with tenant-local data accidentally exposed across tenants.

## 5. Persistence target and migration strategy

### 5.1 Persistence ownership

The Organization capability should own its production persistence adapter and schema for Organization, units, memberships/affiliations, Party association and tenant participation. Ecosystem owns Party persistence. Organization references PartyId through a validated port; it does not write Party tables.

Use the repository's established JDBC/reactive database conventions only after inspecting the selected Organization reactor's parent POM, transaction abstraction, migration runner, testcontainers setup and neighboring production adapters. Do not copy an arbitrary adapter pattern from another module without verifying dependency and reactor compatibility.

### 5.2 Logical schema (not final SQL)

Candidate tables, subject to naming and schema conventions:
- organization;
- organization_unit;
- organization_account_membership (or compatibility-preserved existing membership table);
- organization_party_association;
- organization_person_affiliation;
- organization_tenant_participation;
- organization_identifier (issuer/namespace-scoped legal or registry identifiers).

Every tenant-scoped row must carry TenantId or an explicit ownership scope and enforce it in repository predicates and database constraints where feasible. Organization canonical identity association is global identity metadata and must not be used as a cross-tenant data-access shortcut.

Required database properties:
- primary keys preserve existing OrganizationId values;
- foreign keys within the Organization schema;
- PartyId reference validation at application boundary (cross-capability FK only if deployment/schema ownership explicitly supports it);
- unique active association constraints;
- indexes aligned with tenant-scoped lookup paths;
- optimistic revision/version checks;
- effective-time and recorded-time history where legally significant;
- immutable audit/provenance evidence for mapping corrections;
- transactional outbox if aggregate events are emitted.

### 5.3 Expand, migrate, verify, contract

**Phase A — Expand**
- Add new nullable/side-by-side columns or tables.
- Keep current OrganizationId and tenant-scoped behavior.
- Introduce explicit association APIs and dual-read diagnostics, not dual-write identity guesses.
- Do not change event/API payload types in place.

**Phase B — Backfill only verified mappings**
- Source mappings from authoritative existing data or approved reconciliation.
- Never map equal UUIDs without evidence.
- Record mapping source, confidence/validation state, operator/approver, timestamp and original values.
- Quarantine ambiguous, duplicate or conflicting records for manual resolution.
- Backfill in idempotent batches with checkpoints and per-batch counts/checksums.

**Phase C — Verify**
- Compare source/target counts and stable checksums.
- Check uniqueness and referential integrity.
- Run tenant-isolation and cross-tenant negative tests.
- Verify all active Organization records have expected association state (linked, intentionally unlinked, or quarantined).
- Verify event/API compatibility and consumer behavior.
- Reconcile discrepancies before enabling new reads.

**Phase D — Cut over**
- Enable canonical Party resolution behind a feature flag or explicit capability configuration.
- Preserve OrganizationId as the aggregate key and external compatibility identifier.
- Monitor mismatches, lookup failures, duplicate associations and cross-tenant denials.
- Roll back by disabling the new resolution path; do not delete the mapping evidence.

**Phase E — Contract**
- Remove legacy fields or duplicate profile storage only after all consumers, stored data, external contracts and rollback windows are proven clear.
- Contract migration requires a separate approved change and explicit deprecation timeline.

No migration should rewrite all foreign keys from OrganizationId to PartyId as a blanket operation. Many references are correctly organization-structure references and must remain OrganizationId.

## 6. API and event compatibility

### 6.1 Commands and queries

Potential new commands:
- associate Organization with canonical Party;
- verify/reject association;
- correct/supersede association with reason and evidence;
- add/revoke person affiliation;
- add/revoke tenant participation (only after semantics are settled).

Potential queries:
- resolve canonical Party for OrganizationId;
- resolve Organization for PartyId under an explicit access scope;
- list organizations participating in a tenant;
- list a Party's current/historical organization affiliations.

All queries that expose tenant-owned profile or membership data must require a validated tenant/access scope. A global Party association lookup must not reveal tenant-local organization records.

### 6.2 Event rules

Do not mutate existing OrganizationCreated, membership or unit event schemas in place. Add versioned events only after the event inventory and outbox implementation are verified.

Candidate event families:
- OrganizationPartyAssociationEstablished;
- OrganizationPartyAssociationCorrected;
- OrganizationPartyAssociationRevoked;
- OrganizationPersonAffiliationEstablished/Revoked;
- OrganizationTenantParticipationGranted/Revoked.

Events carry identifiers, revision, effective time, actor/service, tenant context where applicable, correlation/causation and provenance references. They must not contain login credentials, personal profile payloads or unrestricted organization records.

Outbox write and aggregate persistence must be atomic. Consumers must be idempotent and support replay/reconciliation.

## 7. Security and authorization requirements

- Party identity resolution is policy-scoped even though Party is globally identified.
- Tenant membership remains a separate prerequisite for tenant-scoped operations.
- Organization membership or affiliation does not imply authentication.
- UserId-to-PartyId linkage is resolved through the existing explicit PartyIdentityAssociation; never infer it from email, phone or UUID.
- Organization-to-Party association does not imply agency authority, legal ownership, signing authority or control.
- Agency appointment and jurisdiction are owned by the future Public Administration capability.
- Cross-tenant organization references do not grant access to another tenant's data.
- Association establishment/correction requires authorized actor, validated Party kind and auditable evidence.
- High-impact identity corrections require approval or dual control according to deployment policy.

## 8. Test strategy and acceptance criteria

### Domain tests
- OrganizationId and PartyId remain non-interchangeable.
- Party association rejects missing Party and Party kind PERSON.
- Duplicate active mapping is rejected.
- Revocation/correction preserves history and does not silently remap.
- Organization units always remain attached to OrganizationId.
- UserId access membership and PartyId affiliation have distinct invariants.
- Tenant participation does not create cross-tenant data visibility.

### Persistence tests
- Organization, units and memberships round-trip through the selected production adapter.
- Optimistic concurrency rejects stale writes.
- Unique active mapping constraints hold under concurrent writes.
- Tenant-scoped queries cannot return another tenant's records.
- Transaction rollback leaves neither partial aggregate state nor orphan outbox events.
- Migrations work from the actual baseline and are repeatable in CI/Testcontainers.
- Backfill is idempotent and reports conflicts without overwriting data.

### Compatibility tests
- Existing OrganizationId APIs and serialized event payloads remain readable during the deprecation window.
- Existing UserId membership consumers continue to work.
- Existing OrganizationUnit and OrganizationUnitId relationships are unchanged.
- Consumers that need canonical identity use an explicit mapping port.
- Old and new application versions can coexist during rolling deployment where the deployment model requires it.

### Operational tests
- Metrics for unresolved/duplicate mappings, migration lag, failed lookups and tenant denials.
- Reconciliation report is reproducible.
- Feature-flag rollback restores the old resolution path.
- Audit trail can explain who established or corrected each mapping and why.

## 9. Dependency-ordered implementation milestones

| Milestone | Deliverable | Gate |
|---|---|---|
| M1.0a | Complete consumer/schema/API/event/data inventory | No migration until inventory reviewed |
| M1.0b | Decide tenant semantics and membership split | Product/domain decision approved |
| M1.0c | Finalize Party association domain and application ports | Domain invariants and tests approved |
| M1.0d | Select Organization persistence adapter/reactor and transaction/outbox pattern | Build/test baseline proven |
| M1.0e | Implement persistence and compatibility APIs | Integration and migration tests pass |
| M1.0f | Verified backfill and reconciliation tooling | Data owner approves mapping evidence |
| M1.0g | Feature-flagged consumer cutover | Compatibility and security evidence accepted |
| M1.0h | Deprecation/contract proposal | Explicit approval after rollout evidence |

Each milestone should be a substantial coherent change. Do not create a PR for each small subtask. The user must explicitly approve opening a PR.

## 10. Decisions that remain open

The following require additional repository or product evidence and are intentionally not assumed:
- What does the current mandatory Organization.tenantId mean in production: creator, owner, data partition or exclusive tenant?
- Does a production Organization database already exist outside the inspected branch tree or in a deployment-specific module?
- Are any current OrganizationId values intentionally equal to PartyId values, and if so, what is the authoritative evidence?
- Does Identity already own some organization access grants that should replace or integrate with OrganizationMembership?
- Which external organization registration identifiers must be supported first for Indonesia and other localizations?
- What is the supported transaction/outbox integration in the Organization reactor?

## 11. Exit criteria

M1.0 design is ready for implementation only when:
- complete consumer and persistence inventory is reviewed;
- tenantId semantics are documented;
- account access membership and person affiliation are explicitly separated;
- Organization-to-Party association ownership and uniqueness are approved;
- persistence, transaction, migration and outbox patterns are verified against the actual Organization reactor;
- compatibility and rollback plan is accepted;
- security and data-owner requirements are documented.

**This document is a plan, not evidence that Organization convergence is implemented.** No code, migrations, builds, tests, data backfills or PRs are part of this milestone.
