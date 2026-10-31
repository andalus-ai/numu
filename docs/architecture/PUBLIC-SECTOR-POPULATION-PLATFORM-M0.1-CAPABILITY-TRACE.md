# Public Sector & Population Data Platform — M0.1 Source-Level Capability Trace

- **Status:** Source-level trace completed for the first platform foundations; implementation remains gated on identity and authorization design.
- **Repository:** `andalus-ai/numu`
- **Baseline inspected:** `main` at tree `088fada41b3f9bc66f559dee7ad343f6f7ff0841`
- **Working branch:** `feat/public-sector-platform-m0-audit`
- **Related:** [M0 repository audit](PUBLIC-SECTOR-POPULATION-PLATFORM-M0-AUDIT.md)

## 1. Executive decision

The source trace materially refines M0:

1. **Canonical Party identity is implemented more substantially than the initial inventory suggested.** Ecosystem contains a Party aggregate, application repository/lookup port, JDBC repository, and canonical-party migration. Party identity is global and not tenant-owned.
2. **A canonical Person profile aggregate is not established by the inspected contracts.** `PartyKind.PERSON` identifies an individual Party, while `PersonRef` remains a separate UUID reference. Neither should be silently converted into the other.
3. **Organization is not ready to serve as the public-sector agency master without convergence work.** The top-level Organization aggregate is tenant-owned, uses a separate `OrganizationId`, has units and memberships, and currently has only in-memory repository adapters in the inspected tree. Ecosystem also has an Organization profile keyed by PartyId. The repository has already documented this duplication and a safe convergence plan.
4. **Tenant and jurisdiction are separate dimensions.** Tenancy is a SaaS/platform isolation boundary. Government jurisdiction is a legal/geographic scope that may span tenants or be shared across agencies.
5. **Identity and Security already provide useful authorization building blocks, but not a complete government jurisdiction policy model.** Existing role scopes and ABAC are generic; a typed, auditable jurisdiction/agency authorization context is still needed.
6. **Geo is not an administrative geography service.** It supplies validated points, bounds, distance, grid and polyline primitives. The M7 spatial-query contract and the README explicitly leave stored-location queries, topology-aware polygons, administrative-boundary datasets and PostGIS adapters outside the current delivered domain.

**M0.1 recommendation:** Do not start a government-specific Organization aggregate or Citizen/Person aggregate yet. First establish a single canonical Party↔Organization contract and an agency/jurisdiction authorization model that composes with existing Identity, Tenancy, Security and Geo. Then build versioned administrative geography as a distinct public-sector data capability.

## 2. Source trace findings

### 2.1 Canonical Party and Person

Relevant source:
- `core/framework/ecosystem/ecosystem-domain/.../domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyId.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyKind.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyRef.java`
- `core/framework/ecosystem/ecosystem-application/.../application/port/PartyRepository.java`
- `core/framework/ecosystem/ecosystem-application/.../application/port/PartyLookupPort.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V2__canonical_parties.sql`
- `core/framework/foundation/numou-foundation-domain/.../domain/ref/PersonRef.java`

Observed:
- Party is an aggregate whose stated responsibility is canonical cross-domain identity and lifecycle only.
- Party kind is immutable after registration and limited to `PERSON` or `ORGANIZATION` (UNKNOWN is rejected for canonical records).
- Party lifecycle supports active/inactive transitions.
- A public application persistence/lookup port exists.
- JDBC persistence stores Party ID, kind, status, timestamps and optimistic version.
- The JDBC repository explicitly documents Party identity as **global and deliberately not tenant-owned**.
- Party↔external identity associations are tenant-scoped business associations and are explicitly not authorization grants.
- `PersonRef` remains a separate universal UUID reference; it is not an alias or adapter to PartyId.
- Party intentionally does not own names, contact details, organization structure or person-specific attributes.

Implications:
- Reuse PartyId as the canonical identity reference for new cross-domain contracts only after each consumer has an authorized Party lookup/association contract.
- Do not make Party tenant-owned to solve government jurisdiction.
- Do not store national identity numbers, birth details, household data, poverty, criminal records, land rights or other personal profile data directly on Party.
- A person profile/civil-registration capability may reference PartyId, but its authority, legal identity, verification and lifecycle must be explicit.
- Existing PersonRef consumers need a compatibility inventory and an explicit validated mapping before migration. Equal UUID values are not proof of identity equivalence.

### 2.2 Contact and address

Relevant source:
- `core/framework/ecosystem/ecosystem-domain/.../domain/contact/Address.java`
- `PostalAddress.java`, `PartyAddress.java`, `PartyContactPoint.java`, `EffectivePeriod.java`
- `docs/architecture/contact-address-capability-contract.md`

Observed:
- Ecosystem has reusable Address, PostalAddress, ContactPoint, party-address/contact association and effective-period vocabulary.
- Address is independent of party ownership.
- These types provide a useful contact/address foundation, but the source trace does not establish official administrative-unit identifiers, boundary-version references, cadastral parcel geometry or legal residence history as a complete government geography contract.

Implications:
- Reuse the existing address/contact capability for contact channels and postal/physical address semantics.
- Do not overload PostalAddress with all government administrative geography or use free-text address fields as authoritative jurisdiction.
- A validated address-to-administrative-unit resolution should be a separate contract that references a versioned Administrative Geography dataset.
- Legal residence, household membership, mailing address and current physical location are distinct relationships and must not be collapsed into one address field.

### 2.3 Organization convergence and public-sector suitability

Relevant source:
- `organization/organization-domain/.../domain/Organization.java`
- `OrganizationUnit.java`, `OrganizationMembership.java`
- `organization/organization-spi/.../OrganizationRepository.java`
- `organization/organization-adapter/.../InMemoryOrganizationRepository.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/organization/Organization.java` (profile slice)
- `docs/architecture/organization-party-identity-convergence.md`

Observed:
- Top-level Organization is keyed by OrganizationId and requires TenantId.
- It owns legal/display names, registration number, lifecycle status, organizational units and membership.
- OrganizationUnit supports a nested parent-unit reference within an Organization.
- Top-level Organization SPI exists, but the inspected repository implementation is in-memory. The source tree contains no production JDBC/Postgres Organization repository or migrations under this reactor.
- Ecosystem also has an Organization profile keyed by canonical PartyId, with classification vocabulary and tenant association.
- The convergence decision explicitly says not to create a third Organization model, not to equate OrganizationId and PartyId by UUID, and to keep top-level Organization as the intended owner of organizational structure while Party remains canonical identity.
- The current top-level Organization's mandatory tenant association is already identified as a concern for external organizations and multi-tenant participation.

Implications:
- Public agencies should ultimately be Organization records with a public-sector classification and jurisdiction assignments, not a separate kind of login identity or a third Organization aggregate.
- Before public-sector extension, execute the existing convergence plan: explicit Party association, external/multi-tenant semantics, one application workflow, production persistence, migration and consumer compatibility.
- Agency reporting hierarchy and legal/geographic jurisdiction must not be represented only as nested OrganizationUnit records. They are different relationships with independent validity periods and authority.
- Public agency identifiers (for example ministry/agency codes) belong in namespaced jurisdictional registration records, not as a new universal Party ID.

### 2.4 Identity, roles and tenant membership

Relevant source:
- `identity/numou-identity-domain/.../domain/user/User.java`
- `identity/numou-identity-domain/.../domain/role/assignment/RoleAssignment.java`
- `identity/numou-identity-domain/.../domain/role/assignment/RoleScopeType.java`
- `tenancy/tenancy-domain/.../domain/tenant/Tenant.java`
- `tenancy/tenancy-domain/.../domain/membership/TenantMembership.java`
- `security/security-application/.../application/context/TenantGuard.java`

Observed:
- User is explicitly authentication identity only. It does not own tenant, organization, person or permission data.
- RoleAssignment binds a User or ServiceAccount principal to a Role at a scope; it carries scope type, string scope path, grantor, grant time and optional expiry.
- Existing scope types include SYSTEM, ORGANIZATION, SPACE_OR_SOURCE, FOLDER, DATASET, PROJECT, WORKSPACE, RESOURCE and CUSTOM.
- Tenant is a separate aggregate with optional OrganizationRef. Tenancy documentation explicitly defines it as the platform multi-tenancy boundary.
- TenantGuard resolves the active tenant from a validated token claim, a membership-checked header, or a single unambiguous membership.
- Identity's tenant membership and role assignment are not proof of authority over a government jurisdiction.

Implications:
- A public-sector user may be a platform User, linked optionally to a Party, and hold one or more agency appointments/roles with time-bounded jurisdiction scopes.
- A user may work for multiple agencies or across multiple administrative areas without changing their tenant identity.
- A government agency may share a tenant with several agencies or use dedicated tenants; legal authority must not be inferred from tenant membership.
- Existing string scopePath is not sufficient as the canonical legal-jurisdiction model. Add a typed, validated authorization context or policy evidence that resolves agency, jurisdiction, action, record classification and effective time.
- Avoid a global role such as `GOVERNMENT_ADMIN` granting broad access to all citizen records.

### 2.5 ABAC, security context and audit

Relevant source:
- `identity/numou-identity-application/.../application/security/abac/AuthorizationContext.java`
- `AuthorizationPolicy.java`, `AttributeReference.java`, `PolicyEvaluator.java`
- `identity/numou-identity-application/.../application/port/ResourceAttributePort.java`
- `security/security-application/.../application/context/TenantGuard.java`
- `security/security-spi/.../port/ResourceAuthorizationPort.java`
- `security/security-spi/.../port/AuditPort.java`
- `security/security-domain/.../domain/audit/AuditEntry.java`

Observed:
- ABAC already separates subject, resource and environment attributes and supports namespaced attribute references.
- AuthorizationContext requires tenantId and permission, and freezes supported scalar/list/map values.
- ResourceAttributePort resolves attributes for a principal, tenant, authorization requirement and resource ID.
- ResourceAuthorizationPort exposes an authorization evaluation port.
- AuditPort is append-only by interface; AuditEntry includes actor, tenant, action, resource, resource ID, timestamp, correlation ID and changes.
- These contracts are reusable building blocks, but the inspected shape does not establish a complete field-level government data classification, jurisdiction authority graph, lawful-purpose authorization, data-sharing authorization or record-specific access audit policy.

Implications:
- Use Identity ABAC as an enforcement engine only after resource attributes are supplied by trusted domain-owned resolvers and the policy vocabulary is explicitly governed.
- Jurisdiction and agency appointment facts should be sourced from a domain-owned, auditable authorization evidence provider; do not accept them as client-supplied arbitrary attributes.
- Extend audit semantics through a dedicated public-sector audit policy/adapter as needed. Do not put sensitive field values or full before/after citizen records into the existing free-text `changes` field.
- Every access to sensitive records, exports, bulk queries, corrections, delegation and cross-agency shares should create a suitable audit event.
- A tenant guard is necessary but not sufficient for authorization to individual citizen/land/justice records.

### 2.6 Geo and administrative geography

Relevant source:
- `geo/README.md`
- `geo/geo-domain/.../GeoPoint.java`, `GeoBounds.java`, `GeoRadius.java`, `GeoGrid.java`
- `geo/docs/SPATIAL-QUERY-CONTRACT-M7.md`

Observed:
- Delivered Geo domain provides WGS84 point, spherical distance, geographic bounds, exact radius predicate, angular grid bucketing and encoded polyline codec.
- It does not deliver stored-location search, topology-aware polygon operations, administrative boundary datasets or PostGIS adapter.
- The M7 spatial-query contract is a design contract, not proof that a query port or adapter exists.
- Geo README states grid cells are coarse candidate buckets and not a complete candidate set; antimeridian and polar coverage need explicit handling.

Implications:
- Administrative Geography should own jurisdiction unit identity, official codes, hierarchy, historical revisions and boundary-dataset provenance.
- Geo should own shared spatial primitives and, if justified, provider-neutral spatial query semantics.
- A PostGIS adapter may implement storage/query for polygon geometry, but must preserve exact spatial predicates, completeness and tenant/authorization filtering.
- Never use a grid cell as a legal administrative boundary.
- Historical record jurisdiction should be resolved against the applicable dataset/version and effective date, not silently against today's boundary.

## 3. Capability contract map for M1

| Concern | Reuse now | New contract or gap |
|---|---|---|
| Canonical person/organization identity | Ecosystem PartyId and PartyKind | Validated Party lookup and explicit PersonRef compatibility; person-specific profiles stay separate |
| Contact and postal address | Ecosystem Address/PostalAddress/PartyAddress | Versioned address-to-administrative-unit resolution |
| Agency identity | Party + Organization convergence target | Production Organization persistence, Party association, public-sector classification |
| Agency structure | OrganizationUnit and OrganizationMembership | Cross-agency appointments, delegation, effective-dated legal reporting relationships |
| Platform account | Identity User | Explicit optional User↔Party association; no automatic one-to-one assumption |
| Tenant isolation | Tenancy + TenantGuard | Explicit mapping from deployments/agencies to tenants; never infer jurisdiction from tenant |
| Role and policy engine | Identity roles + ABAC | Typed agency/jurisdiction authority evidence and policy vocabulary |
| Business audit | Security AuditPort | Sensitive-record access/export/share/correction audit policy and retention/integrity profile |
| Geographic primitives | Geo | Administrative units, official codes, versioned boundary datasets, topology and spatial storage/query |

## 4. Proposed M1 boundaries

### M1-A — Public-sector organization and authority profile

Extend the existing Organization capability after convergence prerequisites:
- public-sector organization classification (namespaced and versioned);
- jurisdictional registrations/official codes with issuing authority and validity period;
- agency mandate and authority references;
- effective-dated agency-to-jurisdiction assignments;
- cross-agency delegation/appointment records with grantor, scope, purpose, validity and revocation;
- explicit PartyId association and tenant participation independent of Party identity.

Do not add government-specific fields directly to Party. Do not represent every jurisdiction as an OrganizationUnit.

### M1-B — Administrative Geography

A new capability or domain package should own:
- jurisdictional administrative unit ID independent of names/codes;
- jurisdiction type from a versioned classification scheme;
- official code namespace and issuing authority;
- parent-child relationship history;
- valid-from/valid-to and recorded-from/recorded-to semantics where corrections require bitemporal history;
- boundary dataset release/version, source, license, import timestamp, integrity checksum and geometry reference;
- geometry validity and parent-containment validation;
- historical lookup: which unit/boundary applied to a coordinate or record at a given effective date;
- import, validation, reconciliation and release lifecycle.

Do not make Geo depend on Administrative Geography. Administrative Geography may depend on Geo primitives or a spatial-query contract, preserving the reusable direction.

### M1-C — Authorization integration

Before any sensitive registry is implemented, define a stable decision input containing:
- authenticated principal and tenant context;
- canonical Party/organization appointment reference where applicable;
- acting agency;
- jurisdiction scope and hierarchy traversal policy;
- resource owner/agency and jurisdiction;
- record classification and requested action;
- purpose/legal-basis code and case/workflow reference where applicable;
- effective time, delegation status and policy version.

Authorization should fail closed when the jurisdiction, appointment, purpose or record classification cannot be established. The API must not allow callers to assert their own jurisdiction scope.

## 5. Critical design questions to resolve before M1 code

1. Is Organization allowed to exist without a tenant and participate in multiple tenants? The current top-level aggregate says no, while the convergence decision identifies this as an unresolved design issue.
2. What is the authoritative Party↔Organization association and uniqueness policy? It must be persisted and auditable, with no UUID aliasing.
3. Which authority relationship model is needed: administrative hierarchy, legal mandate, operational reporting, delegation, or all as separate typed relationships?
4. Which jurisdiction dataset and licensing terms are approved for Indonesia-first delivery? Confirm official code sets, boundary data, release history and permitted reuse.
5. Which policy dimensions are mandatory for each data class and operation? A general ABAC attribute map is not itself a legal basis or privacy governance framework.
6. What is the tenant deployment model for national, provincial, regency/city and village offices, including shared services and cross-agency collaboration?
7. What is the event/data contract and correction process for historical boundary changes?
8. Which source records can be processed, under what legal authority and purpose? This is especially critical for civil registration, social protection, health/fertility and justice data.

## 6. M1 entry gates

M1 implementation must not begin until the following are resolved or explicitly scoped as prerequisites:
- reconcile the Organization duplication according to the existing convergence decision;
- establish a persisted Party↔Organization association and production Organization persistence path;
- define whether Party profiles and organization records can participate across tenants without changing global Party identity;
- define typed agency/jurisdiction/appointment relationships and their lifecycle;
- define the policy evidence and audit requirements for jurisdiction-scoped access;
- choose the administrative boundary source, license, dataset versioning and historical semantics;
- verify the actual Maven reactors, architecture tests, migrations and cross-reactor consumers.

## 7. Verification record

- Recursive Git tree inventory performed for `main`.
- Inspected canonical Party aggregate, Party application ports, JDBC repository, canonical Party migration, PersonRef, address/contact contracts, Organization aggregates/ports/adapters, Tenant and membership models, Identity User and RoleAssignment, TenantGuard, ABAC context/policy/attribute contracts, ResourceAttributePort, ResourceAuthorizationPort, AuditPort/AuditEntry, and Geo README/M7 contract.
- No application code, schema, migration, or tests changed.
- No Maven build/test was run.
- Repository tree evidence shows no production Organization persistence adapter in the top-level `organization/` reactor; only in-memory adapters were found.
- This is a source-tree trace, not a complete database inventory or legal review. Runtime configuration, deployment wiring, production schemas outside the inspected reactors, and all downstream consumers still require verification.

## References

- `docs/architecture/organization-party-identity-convergence.md`
- `docs/architecture/person-party-identity-mapping-decision.md`
- `docs/architecture/person-workforce-m0-implementation-audit.md`
- `docs/architecture/contact-address-capability-contract.md`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V2__canonical_parties.sql`
- `geo/docs/SPATIAL-QUERY-CONTRACT-M7.md`
- `geo/README.md`
- `security/security-spi/.../AuditPort.java`
