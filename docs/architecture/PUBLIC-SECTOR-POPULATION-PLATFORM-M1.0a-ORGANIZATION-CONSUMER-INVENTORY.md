# Public-Sector Platform — M1.0a Organization Consumer, Schema and Contract Inventory

**Status:** Source-level inventory (partial; evidence and coverage limits stated below)  
**Repository:** `andalus-ai/numu`  
**Branch:** `feat/public-sector-platform-m0-audit`  
**Inspected HEAD:** `1d7feed73ffa4046b96ebbc49f5ee9664e5a0e21`  
**Scope:** Organization identity/profile models, identifiers, memberships, direct references, persistence and event contracts relevant to M1.0 convergence.  
**Implementation status:** Documentation only. No application code, schemas, migrations, data or API contracts changed.

## 1. Executive result

The source inventory reveals a more nuanced situation than a simple "two Organization aggregates must be merged" framing.

There are two distinct models:

1. **Top-level Organization capability** (`organization/`): uses its own `OrganizationId`, is tenant-bound, owns organization units and UserId-based memberships, and has an in-memory adapter in the inspected tree. Its domain describes a legal entity or business unit and operational organizational structure.
2. **Ecosystem Organization profile** (`core/framework/ecosystem`): is keyed by canonical `PartyId`, validates that the Party exists, is active and has kind ORGANIZATION, and owns a Party-attached organization profile (legal/display name, type, status and optional tenant association).

The Ecosystem Organization profile is **not a second independent identity key**: its aggregate identity is PartyId. The top-level OrganizationId is a separate operational aggregate identifier. Their profile fields overlap, but their ownership and lifecycle semantics are not yet proven equivalent. Therefore the previous proposal to automatically retire or absorb the Ecosystem profile is premature. M1.0b must first decide whether these are complementary bounded-context models, a duplicated profile, or a legacy/newer pair—and define the system-of-record for each field.

**Recommendation:** preserve both identifiers and both current contracts. Define an explicit association only if a verified use case requires a top-level Organization aggregate to represent a particular canonical Organization Party. Do not mandate a universal one-to-one mapping before the data and domain semantics are established.

## 2. Repository baseline and evidence method

The inspected branch HEAD is `1d7feed73ffa4046b96ebbc49f5ee9664e5a0e21`. The recursive tree contained 7,518 paths. The inventory:
- inspected exact Organization, Party, membership, repository, application handler, migration, tenancy event, Workforce, Courier and Construction files;
- enumerated paths matching OrganizationId, OrganizationRef, PartyId, PersonRef, organization migrations and related terms;
- attempted GitHub code search for `OrganizationId`, `OrganizationRef`, `PartyId`, `OrganizationMembership`, `organizationId`, `party_id` and `organization_id`; the search integration returned zero results for all terms, so those results are treated as an indexing limitation, not proof of no consumers;
- did not inspect every file body, generated client, external contract, runtime CDI wiring, deployment database or production dataset.

This is a source-level inventory of confirmed references, not a complete whole-repository call graph or production data census.

## 3. Model and ownership inventory

### 3.1 Canonical Party

Confirmed sources:
- `core/framework/ecosystem/ecosystem-domain/.../party/PartyId.java`
- `.../party/PartyRef.java`
- `.../model/Party.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../V2__canonical_parties.sql`
- `.../JdbcPartyRepository.java`

Facts:
- PartyId is a strongly typed UUID wrapper.
- Party is global and not tenant-owned.
- Party has PERSON/ORGANIZATION kind and ACTIVE/INACTIVE status.
- The canonical table has `party_id` primary key, kind, status, timestamps and version.
- PartyRef is a lightweight Party reference with optional/unknown kind.
- Party does not own organization units, tenant membership, login credentials, or public-sector registry attributes.

Decision: retain Party as the cross-domain identity. A PartyId is not a tenant permission and must not be substituted for OrganizationId, UserId or TenantId.

### 3.2 Top-level Organization capability

Confirmed sources:
- `organization/organization-domain/.../Organization.java`
- `.../OrganizationId.java`
- `.../OrganizationUnit.java`
- `.../OrganizationMembership.java`
- `organization/organization-spi/.../OrganizationRepository.java`
- `.../OrganizationMembershipRepository.java`
- `.../OrganizationUnitRepository.java`
- `organization/organization-application/.../CreateOrganizationHandler.java`
- `.../InviteOrganizationMemberHandler.java`
- `organization/organization-adapter/.../memory/InMemoryOrganizationRepository.java`
- `.../InMemoryOrganizationMembershipRepository.java`
- `.../InMemoryOrganizationUnitRepository.java`
- `organization/pom.xml`, `organization/organization-adapter/pom.xml`

Facts:
- OrganizationId is a dedicated UUID value object.
- Organization requires TenantId; it stores name, legal name, registration number and lifecycle.
- OrganizationUnit is keyed independently and references OrganizationId and optional parent OrganizationUnitId.
- OrganizationMembership is keyed independently and references OrganizationId plus Identity UserId, with PENDING/ACTIVE/SUSPENDED/REVOKED lifecycle.
- InviteOrganizationMemberHandler checks Organization tenant equality against command tenant, active status, and IdentityUserPort.exists before creating a membership.
- The repository SPI has Organization, OrganizationUnit and OrganizationMembership ports.
- The inspected adapters are in-memory. The Organization reactor has domain, SPI, application and adapter modules; no JDBC/Postgres adapter, migration directory or production persistence implementation was found in the inspected tree.
- The top-level OrganizationCreated/Suspended/Reactivated/Dissolved and OrganizationUnitCreated/Dissolved domain events carry OrganizationId and tenant context. Membership lifecycle is not shown as an event family in the inspected files.

Implications:
- OrganizationId must remain stable for internal structure and existing consumers.
- Existing membership is an application access/account relationship because it references UserId. It must not be silently reinterpreted as real-world employee/office affiliation.
- TenantId is currently mandatory on the aggregate. Its exact production semantics (exclusive owner, creator tenant, or data partition) are not proven.
- Production persistence is a gate before government agency onboarding.

### 3.3 Ecosystem Organization profile

Confirmed sources:
- `core/framework/ecosystem/ecosystem-domain/.../organization/Organization.java`
- `.../organization/OrganizationType.java`
- `.../organization/OrganizationStatus.java`
- `core/framework/ecosystem/ecosystem-application/.../command/RegisterOrganizationHandler.java`
- `.../port/OrganizationRepository.java`
- `.../port/EcosystemTransactionRunner.java`
- `core/framework/ecosystem/ecosystem-domain/.../event/OrganizationRegistered.java`

Facts:
- Ecosystem Organization extends its aggregate identity from PartyId; it does not create another OrganizationId.
- Registration validates Party existence, ORGANIZATION kind and ACTIVE status through the transaction-scoped Party repository.
- It rejects an already existing profile for the same PartyId.
- The profile stores legalName, displayName, OrganizationType, OrganizationStatus and optional tenant UUID.
- The application port is `findByPartyId(PartyId)`.
- The registration handler saves the profile and publishes its domain events inside the Ecosystem transaction boundary.
- No separate Organization profile JDBC implementation or migration was found in the inspected tree; the repository is exposed as a transaction-scoped port.

Implications:
- This profile is Party-attached metadata, not a competing canonical organization identity.
- Its optional tenant association differs from the top-level Organization's mandatory TenantId.
- The legal/display name and lifecycle fields overlap with top-level Organization, but there is no evidence that both are populated for the same real-world organizations or that they have identical semantics.
- Do not delete, merge or automatically synchronize these fields until the system-of-record and lifecycle ownership are agreed.

## 4. Identifier and consumer inventory

### 4.1 Confirmed direct consumers / references

| Consumer / source | Reference used | Meaning observed | Migration implication |
|---|---|---|---|
| Top-level Organization aggregate | OrganizationId + TenantId | Tenant-bound operational organization | Preserve key and tenant boundary |
| OrganizationUnit | OrganizationId | Structural ownership | Keep OrganizationId |
| OrganizationMembership | OrganizationId + UserId | Account/access membership | Keep UserId semantics; do not convert to PartyId |
| Organization SPI and handlers | OrganizationId | Application commands and repository lookup | Compatibility contract required |
| Courier `OrganizationLookup` | TenantId + OrganizationId | Explicit tenant-scoped lookup | Preserve strict two-key query boundary |
| Courier `OrganizationUnitLookup` | OrganizationId + OrganizationUnitId | Unit lookup scoped by parent organization | Preserve |
| Tenancy `TenantOrganizationChanged` | Foundation OrganizationRef(UUID) | Tenant's legal-organization reference | Clarify target identity semantics before adapter mapping |
| Workforce employment `OrganizationRef(UUID)` | Local wrapper around UUID | Employment's organization reference | Confirm whether it refers to top-level OrganizationId or another organization identity |
| Foundation `OrganizationRef(UUID)` | Generic cross-domain UUID reference | Tenant-level lightweight organization reference | Clarify semantics; wrapper alone does not prove target model |
| Construction evidence migration | raw `requesting_organization_id UUID` | Captures authorized requesting organization context | Identify source API and mapping before any rewrite |
| Ecosystem Organization profile | PartyId | Profile identity is canonical Party | Do not migrate its key to OrganizationId |
| Party identity association | PartyId + tenant + issuer/subject | Login subject ↔ Party linkage | Not Organization membership |
| Party participant association | PartyId + ParticipantId + tenant + role | Party's business relationship to Participant | Not OrganizationMembership or agency appointment |
| Workforce Worker | PersonRef + TenantId | Worker refers to a person identity | Separate PersonRef compatibility track; no OrganizationId conversion implied |

### 4.2 Important contract distinctions

- `PartyIdentityAssociation` links an external authenticated subject to a Party within a tenant. Its source explicitly says it does not grant roles, tenant membership or authorization.
- `PartyParticipantAssociation` is a tenant-scoped business relationship between Party and Participant, with roles such as CUSTOMER, PARTNER, SUPPLIER, SERVICE_PROVIDER, MEMBER and EMPLOYEE. It is not a top-level Organization membership.
- `OrganizationMembership` is UserId-based. It cannot represent a person without a login.
- `PersonRef` and PartyId are separate wrappers. Workforce Worker stores PersonRef; do not cast or equate UUIDs without an explicit mapping contract.
- Foundation OrganizationRef and Workforce OrganizationRef are separate UUID wrappers. Neither wrapper statically guarantees which Organization bounded context owns the referenced UUID.
- TenantOrganizationChanged currently transports the generic Foundation OrganizationRef. Its producer/consumer adapters must be traced before changing the meaning.
- Construction persists a raw UUID column for requesting organization. It must not be assumed to be PartyId or OrganizationId based on the column name alone.

## 5. Persistence and migration inventory

### 5.1 Existing persisted structures confirmed

| Storage | Confirmed schema | Ownership |
|---|---|---|
| `ecosystem_party` | PartyId, kind, status, timestamps, version | Ecosystem |
| `ecosystem_party_identity_associations` | TenantId, PartyId, issuer/subject, status, linked/revoked time | Ecosystem identity linkage |
| `ecosystem_party_participant_associations` | TenantId, PartyId, ParticipantId, role, status, effective/revoked time | Ecosystem business association |
| Top-level Organization | No SQL migration found in inspected Organization subtree | Not established in inspected tree |
| Ecosystem Organization profile | Repository port and transaction handler found; no dedicated SQL migration or JDBC implementation found in inspected tree | Adapter implementation not established |

### 5.2 Migration implications

- No existing OrganizationId-to-PartyId mapping table was found in the inspected Organization/Ecosystem migration paths.
- Do not infer mapping by comparing UUID values.
- Do not rewrite OrganizationUnit or other internal structure references to PartyId.
- Do not assume the absence of a migration in the inspected subtree proves no deployment-specific schema exists.
- A data migration cannot be designed safely until all deployment schemas and production data are inventoried.
- The Construction migration requires a verified backfill source; its migration explicitly fails if existing rows lack a requester organization. This is an example of a strict migration gate, not proof of a general Organization reference contract.

## 6. Tenant semantics inventory

Confirmed:
- Top-level Organization requires TenantId.
- OrganizationMembership invitation compares the command TenantId to the aggregate TenantId.
- Courier OrganizationLookup requires both TenantId and OrganizationId and explicitly rejects global lookup followed by in-memory filtering.
- Ecosystem Organization profile permits a null tenantId.
- Party is global.
- Party identity and participant associations are tenant-scoped.
- Tenancy emits TenantOrganizationChanged with Foundation OrganizationRef.

Not yet established:
- Whether top-level Organization TenantId means owner tenant, creation tenant, data partition or exclusive tenant.
- Whether one top-level Organization can participate in multiple tenants.
- Whether TenantOrganizationChanged references the top-level Organization aggregate, an Ecosystem Party profile or an external organization.
- Whether cross-tenant agencies are currently represented as shared references or duplicated tenant-local organization rows.
- Whether tenant-level organization association is authoritative for legal identity, billing, security or merely display.

Decision: no tenantId removal or multi-tenant schema change until these semantics and all child-record query predicates are audited.

## 7. Event and API compatibility inventory

Confirmed event/API families:
- Top-level Organization lifecycle events use OrganizationId and TenantId.
- Organization unit events use OrganizationUnitId, OrganizationId and optional parent unit ID.
- Ecosystem OrganizationRegistered uses PartyId identity and organization profile attributes.
- TenantOrganizationChanged uses Foundation OrganizationRef.
- WorkerCreated uses PersonRef.
- Construction requester organization is stored as UUID in a tenant-specific evidence operation table.

Required before contract changes:
- Find all publishers, subscribers, event serialization formats, REST/GraphQL DTOs, generated SDKs and external consumers for these events and identifiers.
- Inspect compatibility and schema registry rules, if present.
- Identify API payloads that serialize bare UUIDs and cannot reveal which ID namespace they contain.
- Verify outbox/transaction semantics for both Organization and Ecosystem adapters.
- Define event versioning and dual-read/write only where a real consumer migration requires it.

No event or API payload should be renamed or have its UUID semantics changed in place.

## 8. M1.0b decisions required before designing association or persistence

1. **Model relationship:** Are the Ecosystem Party-attached organization profile and top-level tenant-bound Organization complementary bounded contexts or duplicate profiles? Define ownership for legal name, display name, type/classification, registration identifiers and lifecycle.
2. **Mapping cardinality:** Is an operational Organization aggregate always a legal organization Party, or can it represent a business unit, branch, department, project consortium or internal team? If the latter, a universal one-to-one OrganizationId↔PartyId mapping is invalid; only legal-entity-like Organizations should be linkable to an Organization Party.
3. **Tenant semantics:** What does the current mandatory Organization.tenantId represent? Which data are tenant-owned versus globally shared?
4. **Membership semantics:** Retain UserId access membership; define separate PartyId-based person affiliation only when needed. Do not replace the current relation.
5. **Reference namespaces:** Standardize the semantic meaning of Foundation OrganizationRef and Workforce OrganizationRef without forcing an immediate shared Java type or UUID rewrite.
6. **External organization identifiers:** Decide how issuer, namespace, jurisdiction, validity and verification evidence are represented.
7. **Persistence:** Select a production persistence design for the top-level Organization capability and verify the actual Ecosystem adapter/runtime wiring.
8. **Migration source:** Identify production database schemas, seed data, API clients and external integrations before any backfill.

## 9. Recommended implementation order

| Order | Work | Exit condition |
|---|---|---|
| M1.0a.1 | Finish runtime and schema discovery for Organization and Ecosystem adapters | Production wiring and all migrations located |
| M1.0a.2 | Trace tenancy OrganizationRef producer/consumer and Workforce OrganizationRef origin | Each reference namespace has documented semantics |
| M1.0a.3 | Trace all OrganizationId API/event/consumer paths in selected modules | Compatibility inventory reviewed |
| M1.0b | Decide complementary models vs profile duplication and field ownership | Domain decision recorded |
| M1.0c | Define optional, typed association only for eligible legal-entity Organizations | Cardinality and validation rules accepted |
| M1.0d | Establish Organization persistence and tenancy behavior | Adapter and integration test baseline proven |
| M1.0e | Implement compatibility queries and migration only where verified data exists | Reconciliation and rollback accepted |

M1.0a is not complete until runtime wiring and external data references are inspected. The current inventory is sufficient to correct the design direction, but not to authorize implementation.

## 10. Acceptance criteria for the next design gate

- No assumption that all Organization aggregates represent legal entities.
- No assumption that Ecosystem Organization profile is a duplicate identity.
- OrganizationId, PartyId, PersonRef, UserId, TenantId and generic OrganizationRef remain distinct.
- OrganizationUnit remains owned by OrganizationId.
- UserId access membership remains separate from Party/person affiliation.
- Tenant boundary is enforced independently of canonical Party association.
- Any association is optional and evidence-backed; no UUID equality inference.
- All runtime persistence adapters, event publishers/subscribers, API clients and production schemas are inventoried.
- No destructive migration or PR is opened without explicit approval.

## 11. Verification limitations

This milestone inspected the branch tree and selected source files listed in the repository paths above. GitHub code search returned no indexed matches for the identifier queries used, so it cannot be relied upon for coverage. No full repository clone/grep, production database inspection, generated client inventory, runtime deployment inspection, Maven build, unit test, integration test or CI run was performed.

No application code, migrations, event schemas, API contracts, data or tests were changed or executed by this documentation-only milestone.
