# Public-Sector Population Platform — M0.4 Repository Implementation-Readiness Audit

**Status:** Evidence-based readiness audit  
**Repository:** `andalus-ai/numu`  
**Branch inspected:** `feat/public-sector-platform-m0-audit`  
**Tree baseline:** `0cdd24a75d367597c4eb3679e640fb3b3b77d140`  
**Scope:** Existing Identity, Tenant, Security/ABAC, Audit, Organization, Party, Geo, Event and reliability foundations  
**Implementation status:** Documentation only. No runtime code, migrations, policies or tests were changed or executed by this milestone.

## 1. Executive result

Numou has meaningful reusable foundations for a public-sector platform. The correct next step is not to create another identity, organization, tenant, security, audit, geography or event framework. The next step is to close the integration and ownership gaps in a controlled order, then implement Administrative Geography as the first low-risk vertical slice.

The audit confirms:
- canonical Party identity has a domain aggregate, repository port, JDBC repository and PostgreSQL migration;
- Identity contains tenant membership, roles, scoped assignments, permissions, federation and a tenant-scoped ABAC policy store;
- Security contains tenant resolution, permission evaluation, resource-authorization ports and append-only audit contracts;
- Organization owns organization structure and memberships but the inspected adapters are in-memory and its aggregate is tenant-bound;
- Geo provides validated WGS84 point/bounds/distance/radius/grid/polyline primitives, but not administrative polygon or stored spatial query capabilities;
- Event has a transport-neutral event envelope and Kafka/in-memory adapters; separate reliability/outbox implementations exist in the wider repository.

These capabilities are foundations, not a complete government authorization or registry platform.

## 2. Repository guardrails applied

The audit follows:
- `AGENTS.md`: inspect real contracts and reactors; preserve ownership and compatibility; do not assume a root Maven reactor; do not claim unrun tests; do not open a PR without approval.
- `docs/ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md`: Numou remains independent and application-neutral; security, tenant boundaries, stable contracts and verified completion are mandatory.
- `docs/architecture/ADR-003-reusable-capability-modules.md`: capabilities are business boundaries, not automatically microservices or Maven artifacts; compose the same rules across standalone, modular-monolith and distributed deployments.
- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`: Geo owns neutral geographic primitives; administrative boundary datasets and PostGIS belong in an appropriate adapter/extension, not in generic Geo primitives.

The inspected branch tree has multiple Maven reactor roots. There is no basis for using a single root `mvn` build as proof of repository-wide readiness.

## 3. Evidence inventory and findings

### 3.1 Canonical Party identity

**Status: Existing foundation; usable with controlled references.**

Inspected:
- `core/framework/ecosystem/ecosystem-domain/.../domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyId.java`
- `core/framework/ecosystem/ecosystem-application/.../port/PartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V2__canonical_parties.sql`

Observed:
- Party is explicitly the canonical cross-domain identity for a person or organization.
- Party owns only kind and lifecycle; names, contact details, organizational structure and person-specific attributes are delegated to other capabilities.
- Party identity is global and deliberately not tenant-owned.
- The JDBC repository uses optimistic lifecycle version checks.
- The migration constrains kind to PERSON/ORGANIZATION and status to ACTIVE/INACTIVE.
- Party identity association and Party-participant association are separate domain concepts and must not be confused with login identity or tenant membership.

**Implication:** Government registry records should reference PartyId where they refer to a person or organization. They must not store a second citizen/person identity aggregate or put poverty, fertility, criminal, land, household or civil-registration fields on Party. Global identity does not authorize unrestricted lookup.

**Remaining gap:** The public-sector work still needs an explicit policy-scoped Party resolution contract and a verified OrganizationId-to-PartyId association/migration plan. PersonRef remains a separate compatibility concern; never assume UUID equivalence.

### 3.2 Identity and Tenant

**Status: Existing authentication and tenant membership foundation; not a government authority model.**

Inspected:
- Identity adapter PostgreSQL entities and migrations V1–V6.
- `TenantMembershipEntity`, tenant role and permission entities, scoped role assignments.
- `PostgresMembershipManagementRepository`, `PostgresScopedRoleAssignmentRepository`.
- `security/application/context/TenantGuard.java`.
- `security/application/context/SecurityContext.java`.

Observed:
- Identity includes user, service account, federation, tenant membership, tenant-scoped roles and permissions, scoped role assignments and an ABAC policy table.
- TenantGuard validates tenant claims/header combinations and rejects malformed, mismatched or non-member tenant selection. When no tenant is specified it only infers one if the principal has exactly one membership.
- SecurityContext is built after token validation and tenant resolution and exposes principal, tenant, roles and client IP.
- Identity's authorization policy table is tenant-scoped and stores permission code, ALLOW/DENY effect, JSON condition, active flag and optimistic version.

**Implication:** Reuse Identity for authentication and tenant membership. Do not encode government agency mandate, administrative jurisdiction, public office appointment or delegated authority as tenant roles. A tenant membership must not be interpreted as authority to access a government registry.

**Remaining gap:** The inspected tenant model is not evidence of cross-agency legal authority, lawful purpose, jurisdiction or data-sharing agreements. Those are separate domain facts and decision inputs.

### 3.3 Security, RBAC and ABAC integration

**Status: Reusable components exist; government authorization is incomplete.**

Inspected:
- `security-domain/.../authorization/AccessRequest.java`
- `PermissionEvaluator.java`
- `AccessDecision.java`
- `security-application/.../authz/AuthorizationService.java`
- `GrantResolver.java`
- `security-spi/.../ResourceAuthorizationPort.java`
- `security-spi/.../PolicyPort.java`
- `identity/.../IdentityPolicyAdapter.java`
- `identity/.../V4__saml_replay_and_abac_policy.sql`

Observed:
- AccessRequest carries principal, tenant, action, resource type, resource ID and a generic string context.
- PermissionEvaluator checks tenant membership, permission grant and optional resource-ID scope.
- ResourceAuthorizationPort exists for fine-grained resource policy evaluation.
- IdentityPolicyAdapter maps effective Identity permissions into Security permissions.
- Identity's ABAC policy table stores tenant-scoped JSON conditions.
- The inspected AuthorizationService RBAC decision path constructs AccessRequest with an empty context string. The shown code does not demonstrate a complete resolution/evaluation pipeline for government purpose, agency authority, jurisdiction, appointment, delegation, legal basis, resource classification or field-level disclosure.

**Implication:** Extend the public authorization contract with typed, validated resource and purpose context and explicit trusted fact resolvers. Do not treat a JSON policy row or generic string context as a substitute for authoritative agency/jurisdiction facts.

**Required design work before implementation:**
1. Define a typed authorization request and decision contract that preserves existing AccessRequest compatibility.
2. Define trusted ports for acting agency, appointment/delegation, mandate/jurisdiction, resource ownership/classification and lawful purpose.
3. Specify which policy engine evaluates the decision and how Identity's existing policy store participates.
4. Define fail-closed behavior, decision reason codes, obligations, policy version and bounded caching.
5. Add negative tests for missing, stale, revoked, ambiguous and cross-tenant authority evidence.

### 3.4 Audit

**Status: Append-only audit contract exists; sensitive government access evidence is not yet demonstrated.**

Inspected:
- `security-spi/.../port/AuditPort.java`
- `security-domain/.../audit/AuditEntry.java`
- `security-application/.../authz/AuthorizationService.java`

Observed:
- AuditPort is append-only and exposes append, appendAll and resource-history lookup.
- AuditEntry captures actor, tenant, action, resource, resource ID, timestamp, correlation ID and a changes summary.
- The inspected AuthorizationService explicitly audits denials and exposes an audit-performed path for sensitive allowed actions.
- The inspected contract does not itself require acting agency, declared purpose, lawful-basis reference, authorization decision/policy version, field disclosure obligations, source provenance, case reference or immutable evidence references.

**Implication:** Reuse AuditPort as an integration boundary, but do not assume the existing AuditEntry is a complete government audit schema. Design a compatible extension or domain-specific audit event that records sensitive access decisions and changes without copying restricted payloads into the audit log.

Audit sink failure must never convert a denial into an allow. For protected operations that require durable audit evidence, the application must define whether an unavailable audit sink blocks the operation; the policy must be explicit and tested.

### 3.5 Organization

**Status: Domain boundary exists; production persistence and Party convergence are gates.**

Inspected:
- `organization/organization-domain/.../Organization.java`
- `OrganizationMembership.java`
- `organization/organization-application` command handlers
- `organization/organization-spi` repository ports
- `organization/organization-adapter` in-memory repositories
- `docs/architecture/organization-party-identity-convergence.md`

Observed:
- Organization is keyed by OrganizationId and requires TenantId.
- It owns organization lifecycle, units and membership.
- OrganizationMembership is currently associated with Identity UserId, not directly a canonical PartyId.
- The inspected adapter implementations are in-memory repositories; the inspected top-level Organization capability tree contains no PostgreSQL adapter or migrations.
- Ecosystem also contains an Organization profile keyed by PartyId.
- The convergence decision says not to create a third Organization model, to retain top-level Organization as structure owner, to treat Party as canonical identity and to introduce explicit validated association only after evidence and migration design.

**Implication:** Do not build GovernmentAgency as a parallel organizational structure aggregate. Model agency classification, mandate and authority as government-domain facts linked to the canonical Organization capability. Do not begin public-sector organization onboarding until the Organization convergence plan is accepted and its persistence strategy is established.

**M1 prerequisite:** Produce a separate Organization convergence implementation plan covering Party association, membership semantics (UserId vs Party/person references), tenant participation, persistence, migration and compatibility. This audit does not authorize that migration.

### 3.6 Geo and spatial readiness

**Status: Neutral primitives exist; administrative geography is not implemented.**

Inspected:
- `geo/README.md`
- `geo/geo-domain/.../GeoPoint.java`, `GeoBounds.java`, `GeoDistance.java`, `GeoRadius.java`, `GeoGrid.java`, `GeoPolylineCodec.java`
- `geo/docs/SPATIAL-QUERY-CONTRACT-M7.md`

Observed:
- Geo provides validated WGS84 points, bounds, spherical distance/radius predicates, angular grid bucketing and encoded polylines.
- Geo README explicitly states there is no stored-location query API, topology-aware polygon operation, administrative-boundary dataset or PostGIS adapter.
- M7 is a contract proposal, not a delivered query port or adapter.
- Geo's ownership rules explicitly place administrative boundary datasets and PostGIS in an appropriate extension/adapter, while legal administrative meaning remains outside generic Geo primitives.

**Implication:** Do not place AdministrativeUnit or government code systems inside Geo. Create an Administrative Geography capability that owns legal units, classifications, hierarchy, code namespaces, effective dates and boundary release metadata. Reuse Geo point/bounds semantics. Select a spatial storage/query adapter only after a concrete vertical slice and completeness tests are specified.

### 3.7 Events and reliable delivery

**Status: Shared event contracts and transport adapters exist; transactional delivery is capability-specific.**

Inspected:
- `core/framework/event/event-domain/.../EventEnvelope.java`
- `EventMetadata.java`
- `event-spi/.../EventPublisherPort.java`, `EventSubscriberPort.java`
- Kafka and in-memory adapters.
- Repository tree also contains the core reliability outbox contracts/adapters and multiple capability-specific PostgreSQL outbox implementations.

Observed:
- EventEnvelope is transport-neutral and carries a versioned BusinessEvent plus EventMetadata.
- EventMetadata includes event ID, correlation/causation IDs, optional tenant, participant, actor, occurred time and trace ID.
- Event capability supports in-memory and Kafka adapters.
- The repository has multiple outbox implementations at different maturity levels; no evidence supports assuming one universal transactional outbox adapter is already used by every capability.

**Implication:** Use the shared event envelope and public event contracts. For Administrative Geography and Agency Authority, use the selected owning persistence adapter's transactional outbox or a proven reliability integration. Do not publish directly after a database commit without a durable delivery/reconciliation design. Keep event payloads minimal and free of restricted citizen, justice, health or land-owner data.

### 3.8 Workspace and structured data

**Status: Useful user-facing/configuration capability; not an authoritative registry store.**

The M0.2 inventory found tenant/database/table-scoped structured data with schema validation, optimistic revision and relations. It does not establish legal authority, source provenance, bitemporal history, jurisdiction policy, sensitive field classification or statutory retention. Use Workspace for departmental pages, forms, operational views and collaboration; keep official records in registry-owned models.

## 4. Readiness matrix

| Concern | Existing reusable foundation | Missing for public-sector platform | Readiness |
|---|---|---|---|
| Person/organization identity | Party aggregate, PartyId, JDBC persistence | Scoped resolution and PersonRef/OrganizationId compatibility migration | Partial |
| Authentication | Identity users, federation, service accounts | Government office/agency facts are separate | Reuse now |
| Tenant isolation | Tenant membership, TenantGuard, scoped permissions | Explicit cross-agency exchange rules | Reuse now |
| Organization | Aggregate, units, memberships, SPI | Party convergence and inspected production persistence | Gate |
| RBAC | Permission model and evaluator | No inference of legal authority from role | Reuse, extend |
| ABAC | Tenant-scoped policy persistence, resource port | Typed purpose/jurisdiction/authority evidence pipeline | Partial |
| Audit | Append-only AuditPort and AuditEntry | Full sensitive access/decision evidence and retention policy | Extend |
| Geo | WGS84 primitives, bounds, distance/radius | Polygon/topology, boundary datasets, query adapter | Reuse primitives |
| Events | Versioned envelope, Kafka/in-memory adapters | Selected transactional outbox and consumer replay proof | Partial |
| Reliability | Outbox/inbox patterns across selected capabilities | Select one adapter per new persistence boundary | Reuse patterns |
| Analytics | Governed exchange can be composed externally | Dataset metadata, de-identification and statistical release governance | Contract needed |
| Workspace | Pages/structured data/views | Not suitable as authoritative legal registry storage | Use for UI/workflow only |

## 5. Recommended capability boundaries

Do not add one giant `government` aggregate or a generic database that stores every public record. Establish bounded contexts only where there are distinct owners and invariants.

| Capability | Responsibility | Depends on / integrates with |
|---|---|---|
| Administrative Geography | Administrative units, classification schemes, codes, hierarchy, historical boundary releases | Geo primitives; source adapters; Document for source artifacts where appropriate |
| Public Administration / Agency Authority | Agency classification, mandates, jurisdiction assignments, appointments and delegations | Organization; Party; Security authorization port |
| Civil Registration | Birth, death, marriage and other legally registered vital events | Party references; Administrative Geography; Document; Audit |
| Household Registry | Temporal household composition and residence relationships | Party; Administrative Geography; Civil Registration references |
| Land and Property Registry | Parcels, rights, encumbrances, transfers and cadastral source references | Party; Administrative Geography; Geo/spatial adapter; Document |
| Social Protection | Assessments, eligibility, program enrollment, benefits and appeals | Party; Household; Workflow; Finance/payment integration |
| Agriculture and Production | Farm/holding, crop cycle, livestock, breeding, fisheries and production observations | Party/Organization; Administrative Geography; Asset/Inventory where needed |
| Justice / Public Safety | Restricted cases, proceedings, dispositions and statutory retention | Party; Agency Authority; Audit; strict domain-specific security |
| Government Data Exchange / Statistics | Source registrations, exchange agreements, ingestion provenance, statistical products and disclosure controls | Events, Document, external analytics/lakehouse contracts |

Capabilities may be separate Maven artifacts or deployment units only when their real contracts, dependency graph and operational needs justify it.

## 6. M1 implementation plan — dependency ordered

### M1.0 — Organization convergence implementation plan (design gate)

Deliver:
- authoritative ownership between Party and Organization;
- explicit PartyId association design without UUID aliasing;
- membership semantics across UserId, Party/person and organization membership;
- tenant participation model for multi-tenant organizations and shared government agencies;
- PostgreSQL persistence and migration strategy;
- consumer inventory, compatibility adapters and rollback plan.

No identifier backfill or destructive migration in this planning step.

### M1.1 — Administrative Geography domain foundation

Start only after the Organization plan is reviewed; geography itself does not require Organization persistence to establish its own neutral domain contract.

Deliver:
- classification scheme and unit-type contracts;
- AdministrativeUnitId and issuer/namespace-scoped external codes;
- effective-dated localized names and parent relationships;
- predecessor/successor transitions;
- bitemporal fact history and source provenance;
- boundary release metadata and validation contract;
- domain tests for code uniqueness over time, hierarchy validity, historical lookup and split/merge semantics.

Do not start with national boundary bulk ingestion or a speculative PostGIS schema.

### M1.2 — Administrative Geography persistence vertical slice

Choose a narrow initial slice (for example, import and query a small, versioned administrative hierarchy dataset) and implement:
- one persistence adapter and migration set;
- deterministic as-of hierarchy/code queries;
- source checksum and import report;
- tenant and authorization boundary appropriate to deployment;
- integration tests against the selected database;
- repeatable import/idempotency and rollback/reconciliation behavior.

A spatial polygon adapter is a separate step after geometry source, licensing, topology and query completeness are confirmed.

### M1.3 — Agency Authority contracts and integration

Deliver:
- agency profile linked to Organization through an explicit validated reference;
- mandate and legal basis records;
- jurisdiction assignment;
- appointment and delegation;
- effective/recorded time and revocation history;
- authoritative query ports for authorization resolvers.

Do not add these fields to Identity User, OrganizationUnit or Tenant.

### M1.4 — Purpose-aware authorization and audit vertical slice

Deliver:
- typed access context and compatibility adapter around current AccessRequest;
- trusted agency, appointment, delegation and jurisdiction resolvers;
- purpose/lawful-basis catalog integration;
- resource classification and field disclosure obligations;
- decision version/reason/evidence references;
- append-only sensitive access audit contract;
- negative tests for tenant, purpose, authority, expiry, revocation, ambiguity and audit-sink failure.

### M1.5 — First registry selection

Only after M1.1–M1.4 should the project choose the first operational registry. Prefer a bounded, lower-risk registry slice rather than starting simultaneously with criminal, fertility, poverty and land-owner datasets. Sensitive registries require separate threat models and legal/data-sharing review.

## 7. Non-negotiable acceptance tests

### Identity and tenancy
- Party remains global and is not accidentally tenant-filtered as if it were tenant-owned.
- Cross-tenant Party lookup is still policy-scoped.
- OrganizationId, PartyId, PersonRef, UserId and TenantId cannot be silently substituted.
- A tenant member without an agency appointment cannot access protected registry operations.

### Geography
- Codes are resolved by issuer/namespace and effective date.
- Historical parent relationships and localized names are queryable as-of a date.
- Split/merge/abolition preserves predecessor/successor history.
- Boundary release identity and checksum are immutable after publication.
- Geometry adapter candidate search cannot silently omit true matches.

### Authority and authorization
- Authority requires a mandate and effective jurisdiction assignment.
- Appointment and delegation are checked at the requested effective time.
- Expired/revoked/ambiguous authority fails closed.
- Caller-supplied purpose, agency or jurisdiction is validated against trusted evidence.
- Cross-tenant sharing requires an explicit governed contract.
- Field-level restrictions and obligations are enforced, not just returned as metadata.

### Audit and events
- Protected reads/writes produce required audit evidence.
- Audit failure follows an explicit fail-closed policy for high-risk operations.
- Events carry version, actor, tenant where applicable, correlation/causation and aggregate revision.
- Event payloads do not leak restricted personal/case/land/health information.
- Outbox delivery is replay-safe and consumer idempotency is tested.

## 8. Explicit non-goals for M1

- No universal citizen profile containing every government fact.
- No replacement of Dukcapil, BPS, ATR/BPN or other authoritative systems by assumption.
- No use of Workspace structured tables as a legal registry database.
- No mandatory microservices, national data lake or single-vendor GIS.
- No public criminal-record or vulnerability flags on Party/Person.
- No direct cross-context table writes.
- No bulk ingestion of sensitive records before legal basis, data-sharing authority, retention and security review.
- No PR until the user explicitly approves opening one.

## 9. Verification and limitations

This audit is based on the inspected branch tree, AGENTS.md, selected architecture contracts, and selected source files listed in this document. It is not a complete call-graph, schema, runtime wiring, dependency convergence or security penetration audit. GitHub code search/indexing and the selected source sample do not prove the absence of other consumers or adapters.

No Maven build, unit test, integration test, workflow run or deployment was executed for this documentation-only milestone. The audit must not be read as a claim that any government-specific capability is implemented or production-ready.

## 10. Inspected source references

- `AGENTS.md`
- `docs/ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md`
- `docs/architecture/ADR-003-reusable-capability-modules.md`
- `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md`
- `docs/architecture/organization-party-identity-convergence.md`
- `core/framework/ecosystem/ecosystem-domain/.../domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V2__canonical_parties.sql`
- `identity/numou-identity-adapter/src/main/resources/db/migration/V1__identity_rbac.sql` through `V6__align_service_account_description_type.sql`
- `identity/numou-identity-adapter/.../AuthorizationPolicyEntity.java`
- `identity/numou-identity-adapter/.../IdentityPolicyAdapter.java`
- `security/security-application/.../context/TenantGuard.java`
- `security/security-application/.../authz/AuthorizationService.java`
- `security/security-domain/.../authorization/AccessRequest.java`
- `security/security-domain/.../authorization/PermissionEvaluator.java`
- `security/security-spi/.../port/ResourceAuthorizationPort.java`
- `security/security-spi/.../port/AuditPort.java`
- `security/security-domain/.../audit/AuditEntry.java`
- `organization/organization-domain/.../Organization.java`
- `organization/organization-domain/.../OrganizationMembership.java`
- `organization/organization-adapter/.../memory/`
- `geo/README.md`
- `geo/geo-domain/.../GeoPoint.java`
- `geo/docs/SPATIAL-QUERY-CONTRACT-M7.md`
- `core/framework/event/event-domain/.../EventEnvelope.java`
- `core/framework/event/event-domain/.../valueobject/EventMetadata.java`

