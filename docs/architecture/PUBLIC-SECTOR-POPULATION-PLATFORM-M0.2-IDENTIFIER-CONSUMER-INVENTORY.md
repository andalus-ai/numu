# Public Sector & Population Data Platform — M0.2 Identifier Consumer Inventory

- **Status:** Targeted producer/consumer inventory; repository-wide symbol coverage remains incomplete because GitHub code search returned no indexed matches.
- **Repository:** `andalus-ai/numu`
- **Baseline:** `main` at tree `088fada41b3f9bc66f559dee7ad343f6f7ff0841`
- **Branch:** `feat/public-sector-platform-m0-audit`
- **Related:** [M0 audit](PUBLIC-SECTOR-POPULATION-PLATFORM-M0-AUDIT.md), [M0.1 capability trace](PUBLIC-SECTOR-POPULATION-PLATFORM-M0.1-CAPABILITY-TRACE.md)

## 1. Executive findings

This pass confirms a few important facts and corrects stale architecture statements:

1. **Canonical Party persistence is implemented.** The current tree contains the Party aggregate, repository/lookup ports, PostgreSQL JDBC adapter, migration `V2__canonical_parties.sql`, and PostgreSQL Testcontainers tests. It is global and intentionally not tenant-owned.
2. **The Party model is intentionally minimal.** It owns PartyId, PERSON/ORGANIZATION kind and lifecycle. It does not own person profile, civil-registration, contact/address, organization structure, or government data.
3. **The current Organization convergence document is partly stale relative to main.** It says the Party aggregate/repository/persistence are not yet complete; current main contains them. Its core warnings remain valid: no UUID aliasing, no third Organization model, no destructive migration before consumer inventory.
4. **Workforce directly stores PersonRef.** Worker is tenant-scoped and stores PersonRef; it does not yet store PartyId. Its employment OrganizationRef is a separate UUID wrapper.
5. **CRM has legacy customer/person/contact/address data and a partially wired Ecosystem integration.** CustomerEntity stores company and personal names, contact data, postal address fields, tax ID and other customer-account fields. Its EcosystemParticipantAdapter is disabled by default because the Ecosystem ParticipantRepository has no CDI provider in the inspected repository. This is not evidence of a working Party integration.
6. **Organization remains split.** Top-level Organization uses OrganizationId + mandatory TenantId and owns units/memberships. Ecosystem Organization is a profile keyed by PartyId with optional tenant association. Do not merge these IDs or add a third model.
7. **Workspace Structured Database is a generic tenant-owned configurable data feature, not a substitute for authoritative registries.** Its records have schema validation and optimistic revision; the inspected model does not establish legal record provenance, typed domain invariants, purpose-bound access, effective dating, or a government-grade authorization model.

## 2. Verified identifier and owner inventory

| Identifier / model | Current owner | Verified role | Migration posture |
|---|---|---|---|
| `PartyId` | Ecosystem | Canonical cross-domain Party identity | Target identity for new cross-capability person/organization references, subject to explicit lookup and access contracts |
| `Party` | Ecosystem domain | PERSON/ORGANIZATION kind and active/inactive lifecycle | Keep minimal; do not add personal or government attributes |
| `PartyRef` | Ecosystem domain | Lightweight Party reference with kind | Use only at public boundaries that can validate kind and access |
| `PersonRef` | Foundation | UUID reference used by Workforce Worker | Preserve compatibility; do not cast to PartyId |
| `OrganizationId` | Top-level Organization | Tenant-owned organization aggregate ID | Keep until all producers/consumers and persisted data are inventoried |
| Foundation `OrganizationRef` | Foundation | Generic UUID reference | Must be checked against OrganizationId/PartyId semantics; no automatic conversion |
| Workforce `OrganizationRef` | Workforce | Separate UUID reference in Employment | Compatibility type; explicit translation required if converged |
| `TenantId` | Foundation/Tenancy | Platform isolation context | Never use as agency or geographic jurisdiction |
| `UserId` / identity subject | Identity | Authentication principal | Not a citizen identity; Party linkage is a separate, optional association |

## 3. Source-backed consumer inventory

### 3.1 Ecosystem Party

Verified paths:
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/model/Party.java`
- `.../domain/party/PartyId.java`, `PartyKind.java`, `PartyRef.java`
- `core/framework/ecosystem/ecosystem-application/.../PartyRepository.java`
- `.../PartyLookupPort.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`
- `.../db/migration/V2__canonical_parties.sql`
- `.../JdbcPartyRepositoryPostgresTest.java`

Evidence:
- Party kind is immutable after registration; UNKNOWN cannot be persisted as canonical kind.
- JDBC persistence has optimistic lifecycle version checks.
- Party identity is globally unique and not tenant-owned.
- Party repository exposes read/save; consumers can use a lookup port without owning Party persistence.
- PostgreSQL integration tests cover create/read, lifecycle versioning and rejection of UNKNOWN.

**Decision:** Party is sufficiently established as a canonical identity owner for new designs. This does not mean every existing consumer has been migrated or that every caller is authorized to resolve arbitrary global Party IDs.

### 3.2 Party-to-authentication association

Verified paths:
- `PartyIdentityAssociation.java`
- `PartyIdentityAssociationRepository.java`
- `JdbcPartyIdentityAssociationRepository.java`
- `V1__party_identity_associations.sql`
- `JdbcPartyIdentityAssociationRepositoryPostgresTest.java`

Evidence:
- Association explicitly links a tenant, PartyId and external identity subject.
- Active identity subject uniqueness is tenant-scoped.
- Revocation retains history.
- The domain comment explicitly says this linkage does not grant roles, tenant membership or resource authorization.

**Decision:** This association is not a government citizen login model by itself. It is an authentication-subject-to-Party link. Do not infer that every citizen has an account, or every account is a citizen.

### 3.3 Party-to-participant business relationship

Verified path:
- `V3__party_participant_associations.sql`

Evidence:
- This is a tenant-scoped association from PartyId to ParticipantId with a role (CUSTOMER, PARTNER, SUPPLIER, SERVICE_PROVIDER, MEMBER, EMPLOYEE, OTHER), status and effective period.
- It is distinct from the authentication subject association.
- It is not an OrganizationId↔PartyId mapping and does not establish a government agency jurisdiction relationship.

**Decision:** Preserve the distinction between canonical identity, authenticated account association, and business/participation relationship.

### 3.4 Workforce

Verified paths:
- `workforce/workforce-domain/.../worker/Worker.java`
- `workforce/workforce-domain/.../employment/OrganizationRef.java`
- `workforce/workforce-domain/pom.xml`, `workforce/pom.xml`

Evidence:
- Worker contains TenantId, PersonRef, WorkerType, status and audit metadata.
- Worker creation event carries PersonRef.
- Employment uses a Workforce-specific OrganizationRef UUID wrapper.
- Workforce domain POM depends on Foundation, not Ecosystem.
- Workforce is an independent reactor with domain, SPI, application, adapter, JDBC persistence and HRIS modules.

**Decision:** Workforce is a confirmed PersonRef consumer. Any Party migration must be staged and preserve historical worker references, multiple engagements and tenant-scoped authorization. Do not replace PersonRef in one sweeping refactor.

### 3.5 CRM / Customer

Verified paths:
- `crm/numou-crm-adapter/.../persistence/entity/CustomerEntity.java`
- `crm/numou-crm-adapter/.../participant/EcosystemParticipantAdapter.java`
- `crm/numou-crm-adapter/pom.xml`
- `crm/numou-crm-application/pom.xml`

Evidence:
- CustomerEntity persists company name, first/last name, email, phone, address, city, state, postal code, country, industry, website, tax ID, account status and nested contact/address collections.
- Tenant ID is nullable for unreconciled legacy rows; comment says tenant-scoped operations must not expose those rows.
- CRM adapter and application POMs depend on Ecosystem application.
- The inspected EcosystemParticipantAdapter implements a ParticipantPort, but is gated off with `numou.crm.ecosystem-participant.enabled=false`; its comment says there is no CDI provider for ParticipantRepository anywhere in Ecosystem.

**Decision:** CRM is a confirmed data-overlap and integration candidate, but the adapter is not proof of an active Party integration. Later CRM convergence must distinguish a Customer role/account from a Party identity and must preserve transaction/account-specific values and legacy tenant handling. Do not mass-delete CustomerEntity fields until reads/writes, migration and API contracts are traced.

### 3.6 Organization

Verified paths:
- `organization/organization-domain/.../Organization.java`
- `organization/organization-domain/.../OrganizationUnit.java`
- `organization/organization-domain/.../OrganizationMembership.java`
- `organization/organization-spi`, `organization/organization-adapter`
- `core/framework/ecosystem/ecosystem-domain/.../organization/Organization.java`
- `docs/architecture/organization-party-identity-convergence.md`

Evidence:
- Top-level Organization uses OrganizationId and requires TenantId. It owns organization lifecycle, units and memberships.
- Its reactor includes domain, SPI, application and adapter; the inspected adapter implementations are in-memory.
- Ecosystem Organization profile uses PartyId as aggregate ID, has organization classification, legal/display names, lifecycle and optional tenant association.
- Ecosystem profile validates Party kind through a lookup port at registration application boundary.
- The convergence document says top-level Organization remains owner of structure and management, while Party is canonical identity.

**Decision:** Retain the current target responsibility split, but update the convergence plan's stale Party persistence status. The outstanding convergence work is primarily explicit persisted Party association on the organizational-management aggregate, tenant participation semantics, production persistence, one application workflow, and consumer migration—not creation of Party from scratch.

### 3.7 Workspace Structured Database

Verified paths:
- `workspace/workspace-domain/.../DatabaseRecord.java`
- `.../DatabaseTable.java`
- `workspace/workspace-application/.../StructuredDatabaseService.java`
- `workspace/workspace-persistence-jdbc/.../JdbcStructuredDatabaseRepository.java`

Evidence:
- Generic records are tenant/database/table scoped.
- Table schema validates property keys and values.
- Record updates require an expected revision and increment the revision.
- Relations are generic table relations.
- The inspected model does not define domain-specific legal registrations, record authority, source provenance, bitemporal history, jurisdiction policy, field-level classification, retention or data-sharing contracts.

**Decision:** Workspace is valuable for government department workspaces, configurable forms, case-work surfaces and user-defined operational views. It must not become the authoritative database for civil registration, land rights, criminal justice or social-benefit eligibility. Domain registries can expose safe projections and links to Workspace.

## 4. Consumer coverage limitations

A full repository-wide call-site inventory is not complete:
- GitHub code-search API returned zero results with `incomplete_results=true` for PersonRef, PartyId, OrganizationId and OrganizationRef. This is an index limitation, not proof that the identifiers have no consumers.
- The recursive tree identifies file paths, not source contents. Only the source files listed above and existing architecture audits were opened.
- Runtime wiring, generated clients, serialized payloads, external API contracts, event schemas, database columns and historical data have not been exhaustively inspected.
- Existing architecture documents name CRM, Workforce, Finance, Procurement, POS, Courier/Cargo and other potential consumers, but those names are not counted as verified call sites in this pass.

Before any identifier migration, the next inventory must include:
1. Full content-level search across all Java, Kotlin, SQL, YAML, JSON, Avro/Protobuf/AsyncAPI/OpenAPI, test fixtures and migration files.
2. All public DTOs, REST resources, event publishers/consumers and serialization compatibility.
3. All persistence columns/indexes/foreign keys, seed data, data exports and stored documents.
4. Runtime CDI wiring and dependency graph for PartyRepository, PartyLookupPort and all translation adapters.
5. Consumer-specific uniqueness, tenant scope, retention and deletion/merge semantics.
6. A data reconciliation report that distinguishes mapped, unmapped, ambiguous, orphaned and unauthorized legacy identifiers.

## 5. Safe convergence sequence

### Step 1 — Stabilize canonical Party contracts
- Keep Party global and tenant-neutral.
- Document who can resolve which Party under which purpose and access policy.
- Add a typed Party lookup contract that can validate kind and authorization without leaking persistence.
- Keep identity association, tenant membership, business participation and organization management as separate relationships.

### Step 2 — Converge Organization without third identity model
- Preserve OrganizationId during compatibility period.
- Add an explicit PartyId association to the top-level Organization only through a validated application command and persistence migration.
- Decide whether organizations can participate in multiple tenants and how external organizations are represented.
- Add production persistence using the established persistence conventions.
- Keep OrganizationUnit and OrganizationMembership tied to the organizational aggregate.
- Retire the Ecosystem Organization profile only after consumer/data migration and contract tests.

### Step 3 — Migrate PersonRef consumers individually
- Start with Workforce only after its producer, persistence, events and API are inventoried.
- Add explicit PersonRef↔PartyId translation only where the source identity is proven and authorized.
- Preserve existing PersonRef values until a reconciliation proves the mapping.
- Migrate CRM separately; its customer account/contacts are not interchangeable with Party.
- Do not change unrelated capabilities in the same milestone.

### Step 4 — Public-sector domain foundation
- Use PartyId for references to people and organizations.
- Use Organization's converged model for agencies and institutions.
- Introduce typed AdministrativeUnitId and jurisdiction relationships independent of PartyId, OrganizationId and TenantId.
- Keep person profile, household, vital events, land rights, social protection and justice records in their own bounded contexts.

## 6. M0.2 exit and M1 entry decision

M0.2 establishes enough evidence to decide that:
- Party is the canonical identity owner and already has PostgreSQL persistence.
- No third Person or Organization identity aggregate should be introduced.
- Top-level Organization convergence and production persistence remain prerequisites.
- PersonRef migration is a compatibility program, not a UUID cast.
- Workspace Structured Database is a supporting workspace feature, not the legal/authoritative registry store.
- A full consumer/data inventory remains a hard gate for destructive migration.

**M1 should therefore start with an Organization convergence implementation plan and Administrative Geography domain contract, not a new Citizen aggregate.** These can proceed as separate, bounded workstreams. M1 code must not silently depend on unresolved PersonRef migration.

## 7. Verification record

- Recursive Git tree inspected at the stated main tree SHA.
- Source files opened for Party, PartyRepository, PartyLookupPort, JdbcPartyRepository, Party identity association, Party-participant migration, Organization aggregates, Worker, Workforce OrganizationRef, CRM CustomerEntity, CRM EcosystemParticipantAdapter, Workspace DatabaseRecord/DatabaseTable and relevant Maven POMs.
- PostgreSQL Party integration test source inspected; tests were not executed.
- No application code, schema or migration changed.
- No Maven build or CI run was performed.
- Repository-wide code search was incomplete; this report explicitly limits its claims to the verified source paths.

## References

- `docs/architecture/organization-party-identity-convergence.md`
- `docs/architecture/person-party-identity-mapping-decision.md`
- `docs/architecture/person-workforce-m0-implementation-audit.md`
- `docs/architecture/contact-address-capability-contract.md`
- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/java/tech/kayys/numou/ecosystem/persistence/jdbc/JdbcPartyRepository.java`
- `workforce/workforce-domain/src/main/java/tech/kayys/numou/workforce/domain/worker/Worker.java`
- `crm/numou-crm-adapter/src/main/java/tech/kayys/numou/crm/infrastructure/persistence/entity/CustomerEntity.java`
- `workspace/workspace-domain/src/main/java/tech/kayys/numou/workspace/domain/DatabaseRecord.java`
