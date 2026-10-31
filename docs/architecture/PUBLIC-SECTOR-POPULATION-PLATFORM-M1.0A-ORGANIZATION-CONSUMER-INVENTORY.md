# Public-Sector Platform — M1.0a Organization Convergence Inventory

**Status:** Source-level inventory baseline; not yet complete  
**Branch:** `feat/public-sector-platform-m1-0a-inventory`  
**Scope:** Identify existing Organization/Party identity contracts and the remaining evidence required before changing application code, persistence, APIs, events, or data.

> This document records only source evidence inspected for this milestone. It is deliberately not a claim that every repository consumer, API, event, migration, deployment schema, or production dataset has been inventoried. No identifier mapping or migration may be inferred from this baseline.

## 1. Executive result

The repository has two distinct Organization representations:

1. The top-level `organization/` capability owns an Organization aggregate keyed by `OrganizationId`, a mandatory `TenantId`, organizational units, and UserId-based organization membership.
2. Ecosystem has an Organization profile keyed by canonical `PartyId`, with organization-specific profile/lifecycle fields and optional tenant association.

The models have different identity and ownership semantics. The current evidence does not establish a persisted `OrganizationId ↔ PartyId` mapping, a safe equivalence between their UUID values, or a complete canonical Party persistence contract.

**M1.0a outcome:** the domain-level contract inventory is sufficiently concrete to protect the next implementation decisions, but consumer/API/event/data inventory remains open. M1.0b–M1.0e must remain gated.

## 2. Inspected source evidence

The following files were fetched from `feat/public-sector-platform-m0-audit` and inspected directly.

| Area | Source | Verified finding |
|---|---|---|
| Organization domain | `organization/organization-domain/src/main/java/tech/kayys.numou/organization/domain/Organization.java` | Aggregate identity is `OrganizationId`; mandatory immutable `TenantId`; owns name, legal name, registration number and lifecycle; emits Organization lifecycle events. No Party reference is present. |
| Organization identifier | `organization/organization-domain/src/main/java/tech/kayys/numou/organization/domain/OrganizationId.java` | Strongly typed UUID wrapper implementing `DomainId<UUID>`. |
| Organization unit | `organization/organization-domain/src/main/java/tech/kayys/numou/organization/domain/OrganizationUnit.java` | Unit is separately identified and attached to `OrganizationId`; optional parent unit supports hierarchy. |
| Account membership | `organization/organization-domain/src/main/java/tech/kayys/numou/organization/domain/OrganizationMembership.java` | Membership is keyed by its own ID and references `OrganizationId + UserId`; lifecycle is pending/active/suspended/revoked. It is not a real-world Party affiliation. |
| Organization repository port | `organization/organization-spi/src/main/java/tech/kayys/numou/organization/spi/OrganizationRepository.java` | Asynchronous repository contract over `Organization, OrganizationId`. |
| Membership repository port | `organization/organization-spi/src/main/java/tech/kayys/numou/organization/spi/OrganizationMembershipRepository.java` | Lookup is `findByOrganizationAndUser(OrganizationId, UserId)`; no Party-based affiliation query exists in this port. |
| Unit repository port | `organization/organization-spi/src/main/java/tech/kayys/numou/organization/spi/OrganizationUnitRepository.java` | Repository contract is for OrganizationUnit; unit relationship remains OrganizationId-based. |
| Organization module reactor | `organization/pom.xml` | Reactor contains domain, SPI, application and adapter modules. |
| Organization domain POM | `organization/organization-domain/pom.xml` | Depends on foundation and identity-domain; no Ecosystem domain dependency is declared. |
| Ecosystem Organization profile | `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/organization/Organization.java` | Profile aggregate is keyed by PartyId; owns legal/display name, type, status and optional UUID tenant association. Caller is responsible for ensuring Party kind ORGANIZATION. |
| Party identifier | `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyId.java` | Separate strongly typed UUID wrapper; not interchangeable with OrganizationId at the Java type level. |
| Party identity association | `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyIdentityAssociation.java` | Tenant-scoped mapping between external identity subject and Party; explicitly does not grant roles, tenant membership or authorization. |
| Party participant association | `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/party/PartyParticipantAssociation.java` | Tenant-scoped business classification between Party and Ecosystem Participant; explicitly not authorization or login membership. |
| Ecosystem migration V1 | `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V1__party_identity_associations.sql` | Creates tenant-scoped Party identity links, active-subject uniqueness and history indexes. This is not an Organization-to-Party mapping table. |
| Organization convergence ADR | `docs/architecture/organization-party-identity-convergence.md` | Confirms duplicate profile/management models and gates convergence on inventory and canonical Party persistence evidence. |
| M1.0 implementation plan | `docs/architecture/PUBLIC-SECTOR-POPULATION-PLATFORM-M1.0-ORGANIZATION-CONVERGENCE-IMPLEMENTATION-PLAN.md` | Establishes staged expand/migrate/verify/cutover/contract strategy and prohibits destructive identifier replacement. |

## 3. Contract inventory

### 3.1 Identifiers and relationships

| Identifier / relationship | Existing owner | Current meaning | Migration treatment |
|---|---|---|---|
| `OrganizationId` | Organization | Top-level Organization aggregate identity | Preserve; never mass-rewrite to PartyId |
| `PartyId` | Ecosystem Party vocabulary | Canonical person/organization identity | Keep distinct; require explicit validated association |
| `OrganizationUnitId` | Organization | Unit identity | Preserve; units continue to reference OrganizationId |
| `OrganizationMembershipId` | Organization | Organization account-membership record | Preserve during compatibility period |
| `UserId` | Identity | Authenticated account/principal | Remains account/access membership key |
| `TenantId` | Foundation tenancy vocabulary | Tenant scope | Existing Organization aggregate requires it; semantics still need owner/product confirmation |
| External identity subject | Ecosystem identity association | Issuer + subject linked to Party within tenant | Not an organization membership or authorization grant |
| Ecosystem ParticipantId | Ecosystem | Participant identity | PartyParticipantAssociation is a distinct business relationship |

### 3.2 Domain ownership and dependency direction

- Organization owns its structure and management invariants: Organization, OrganizationUnit and current UserId membership.
- Ecosystem Party vocabulary owns canonical Party identity and Party kind.
- Ecosystem Organization profile is keyed by PartyId but is not the owner of OrganizationUnit or UserId membership.
- Organization domain POM does not depend on Ecosystem domain. Do not add a reverse dependency merely to read Party persistence or implementation classes.
- Any Party validation must be an application-level port/contract, with an adapter at the composition boundary. Organization domain must remain free of Ecosystem JDBC, identity-provider and HTTP dependencies.
- Party identity linkage, tenant membership, organization access membership, person affiliation and public-office appointment are different relationships and must not be collapsed.

## 4. Compatibility and migration hazards

1. **Identifier collision:** both IDs wrap UUID, but equality of their raw UUID values is not evidence of identity equivalence.
2. **Tenant semantics:** Organization requires TenantId while Ecosystem Organization allows a nullable tenant UUID. The source does not prove whether the former means creator, owner, exclusive tenant or data partition.
3. **Membership semantics:** existing OrganizationMembership is UserId-based. Replacing UserId with PartyId would change the meaning and break access-oriented consumers.
4. **Unit references:** OrganizationUnit references OrganizationId and must continue doing so because it is an internal structure relationship.
5. **Profile duplication:** Ecosystem Organization profile must not expand into a second hierarchy/membership owner. Its stored data and consumers need an explicit consolidation plan.
6. **Party validation:** the Ecosystem Organization source says its caller must ensure Party kind ORGANIZATION; the inspected source does not itself prove a complete Party aggregate/repository validation path.
7. **Tenant isolation:** a global Party association must never become a shortcut for reading tenant-local Organization profile, unit, membership or other data.

## 5. Evidence still missing — M1.0a is not closed

The following work is required before calling the inventory complete.

### 5.1 Repository-wide consumer inventory

Search and inspect every producer/consumer of:
- `OrganizationId`, `PartyId`, `OrganizationRef`, `PersonRef`, `LegalEntityRef`;
- OrganizationRepository, OrganizationMembershipRepository and OrganizationUnitRepository;
- OrganizationCreated, OrganizationSuspended, OrganizationReactivated, OrganizationDissolved and OrganizationUnit events;
- Ecosystem Organization registration/profile commands, queries and events.

The GitHub code-search connector returned no matches during this pass, so its empty results cannot be used as proof that references do not exist. A repository-wide checkout/grep or a functioning code-search index is required to close this section.

### 5.2 Persistence inventory

Still inspect:
- all Organization adapter source files and whether each adapter is test-only/in-memory or production-backed;
- Organization schema/migrations, if any, plus deployment-specific schema definitions;
- Ecosystem Organization persistence tables, migrations, repositories and registration handlers;
- all seed data, fixtures and integration-test datasets;
- migration ordering, migration runner configuration and actual database baseline.

The inspected V1 Party identity migration is unrelated to Organization-to-Party association. V2/V3 and the complete Ecosystem migration set must be read before a persistence design is selected.

### 5.3 API and event inventory

Still inspect:
- REST/GraphQL/resource endpoints, request/response DTOs and generated clients;
- published OpenAPI/AsyncAPI/Avro/JSON schemas and serialized event payloads;
- message topics, routing keys, inbox/outbox implementations and consumers;
- public SDKs, integration adapters and external API contracts.

No API or event schema is changed by this milestone.

### 5.4 Runtime and data evidence

Still establish:
- the exact Organization and Ecosystem Maven reactor build commands;
- Organization UnitOfWork/transaction, event dispatch and outbox conventions;
- current optimistic-concurrency and aggregate rehydration behavior;
- tenant creation/ownership/participation flows and tenant query predicates;
- production data owners, external integrations and authoritative mapping sources;
- whether any existing OrganizationId values are backed by Party records, based on evidence other than UUID equality.

## 6. M1.0a closure checklist

- [x] Confirm OrganizationId and PartyId are distinct Java types.
- [x] Confirm Organization aggregate currently requires TenantId.
- [x] Confirm OrganizationUnit remains OrganizationId-based.
- [x] Confirm current OrganizationMembership is UserId-based.
- [x] Confirm Ecosystem Organization profile is PartyId-based and tenant is optional.
- [x] Confirm PartyIdentityAssociation and PartyParticipantAssociation do not grant authorization.
- [x] Confirm Organization module has domain/SPI/application/adapter reactor modules.
- [ ] Complete all repository-wide identifier and repository-port call sites.
- [ ] Complete persistence/migration/seed/fixture inventory.
- [ ] Complete API, DTO, event-schema and consumer inventory.
- [ ] Verify transaction, outbox and rehydration conventions in the Organization reactor.
- [ ] Obtain authoritative tenant semantics and production-data ownership evidence.
- [ ] Review the complete inventory before authorizing M1.0b–M1.0e.

## 7. Implementation decision

Do not add an Organization-to-Party association table, change aggregate identifiers, introduce Party-based affiliation, or alter tenant ownership in this inventory milestone. Those changes depend on the missing evidence above.

The next safe implementation task is to complete the repository-wide source inventory from a full checkout or functioning GitHub code-search index, then record the verified consumer and schema matrix. Once that is reviewed, M1.0b can settle tenant and membership semantics; M1.0c can introduce the association contract without guessing at persistence or consumer behavior.

**No application code, schema, migration, API, event contract, or production data was changed by this milestone.**
