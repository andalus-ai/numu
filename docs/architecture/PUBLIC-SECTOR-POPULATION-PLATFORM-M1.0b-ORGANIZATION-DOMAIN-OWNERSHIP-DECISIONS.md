# Public-Sector Platform — M1.0b Organization Domain Ownership Decisions

**Status:** Recommended domain decision proposal; approval required for unresolved product-policy choices  
**Repository:** `andalus-ai/numu`  
**Branch:** `feat/public-sector-platform-m0-audit`  
**Scope:** Clarify ownership and semantics of canonical Party, the top-level Organization capability, the Ecosystem Organization profile, tenant participation, memberships and organization references before implementation.  
**Implementation status:** Documentation only. No code, schema, migration, API, event or data changes.

## 1. Executive decision proposal

The M1.0a inventory changes the framing of M1.0. The two Organization models are not proven duplicate aggregates:

- The Ecosystem Organization profile is keyed by canonical `PartyId`. It is Party-attached organizational classification/profile data.
- The top-level Organization aggregate is keyed by `OrganizationId`, requires `TenantId`, and owns operational structure, units and UserId-based access membership.

**Recommended direction:** retain both models as distinct but potentially related contexts until their lifecycle and field ownership are demonstrated. Do not merge, delete, mirror or automatically synchronize them. Do not require every top-level Organization to map to a Party.

This document supersedes the premature assumptions in M1.0 that the top-level Organization universally owns all organization profile fields and that the Ecosystem profile should be absorbed or retired. M1.0 remains useful for preserving identifiers and separating membership meanings, but its profile-convergence target is not approved.

The proposal is deliberately conservative: it identifies recommended ownership and explicit decision gates rather than pretending that unresolved business semantics have already been agreed.

## 2. Evidence and governing constraints

M1.0a confirmed:
- Party is the canonical cross-domain identity, with `PartyId` and PERSON/ORGANIZATION kind.
- Ecosystem Organization uses `PartyId` as its aggregate identity and validates Party existence, kind and active status at registration.
- Top-level Organization has its own `OrganizationId`, mandatory `TenantId`, organization units and `UserId`-based membership.
- Top-level Organization's inspected adapters are in-memory; no production JDBC/PostgreSQL adapter or migration was found in the inspected subtree.
- Ecosystem Organization's inspected repository is a port; no dedicated JDBC implementation or migration was found in the inspected tree.
- Existing consumers use OrganizationId, Foundation OrganizationRef, Workforce OrganizationRef, PartyId and raw UUIDs with semantics that are not interchangeable.
- The inventory is partial. It is not a complete call graph, production-data census, runtime CDI wiring audit or external-client inventory.

Governing repository rules:
- A capability is a business boundary, not automatically a service, database or Maven artifact.
- Domain behavior must remain deployment-neutral.
- Cross-capability integrations use public ports or versioned remote contracts, not shared private tables.
- Tenant isolation and authorization must be enforced at authoritative boundaries.
- Reuse concepts only where meaning, invariants, lifecycle and compatibility are genuinely common.

## 3. Proposed ownership model

### 3.1 Party — canonical identity

Party owns:
- Stable `PartyId`.
- Party kind (PERSON or ORGANIZATION).
- Canonical identity lifecycle.

Party does not own:
- Tenant access or tenant data ownership.
- Organization operational structure or units.
- User credentials, roles or login membership.
- Agency mandate, jurisdiction or public office.
- Land title, civil-registration facts, employment or other domain records.

A Party is not an authenticated user. A Party identity link to an external identity subject does not itself grant access.

### 3.2 Ecosystem Organization profile — Party-attached profile

**Recommended current interpretation:** retain this as the profile/classification context attached to an Organization Party, keyed by `PartyId`.

It may own profile fields such as:
- Organization type/classification where the classification describes the Party as a real-world organization.
- Display or preferred name, if this is a Party-facing profile concept.
- Profile status, provided its lifecycle is explicitly distinguished from Party lifecycle and operational Organization lifecycle.
- Optional tenant association only if its meaning is clarified; the existing optional tenant UUID must not silently become a security boundary.

It must not become the owner of:
- Operational units and internal reporting hierarchy.
- Tenant application access memberships.
- Agency jurisdiction or appointment.
- Legal authority evidence unless a dedicated legal-registration contract is explicitly assigned to it.

The current overlapping legal/display name and status fields need field-level ownership decisions before either model is changed.

### 3.3 Top-level Organization — operational organization context

**Recommended current interpretation:** retain this as the operational organization aggregate used by organization structure and current application consumers.

It owns:
- Stable `OrganizationId`.
- Operational organization lifecycle as currently implemented.
- Organization units and internal structure.
- Existing UserId-based organization access membership, preserving its current compatibility meaning.
- Operational metadata required by its existing consumers.

Do not assume that every instance represents a separately registered legal entity. It may represent a tenant-local operating organization, branch, department, team or other organizational construct only after the domain contract explicitly supports those types. The current code's exact supported semantics must be verified before extending them.

Do not copy all Ecosystem Organization profile fields into this aggregate merely to make the models look uniform.

### 3.4 Relationship between the two Organization contexts

A relationship is **optional**, not universal.

If a top-level Organization represents or operates on behalf of a canonical Organization Party, establish an explicit, validated association through a public contract. The association must not be inferred from equal UUIDs or from a matching name/registration number alone.

Recommended initial cardinality:
- A top-level Organization has zero or one active canonical Organization Party association.
- A Party may be associated with zero or more operational Organizations only if the business model explicitly permits multiple operating representations (for example, separate tenant-local operating contexts). This must not imply multiple legal identities.
- If the platform later requires strict one-to-one representation for a specific Organization subtype, enforce it for that subtype through a documented invariant rather than imposing it globally.

Association evidence should include source, verification state, effective period, recorded time/actor, revision and correction/supersession history. Association creation or correction does not grant membership, tenant access, agency authority or legal control.

**Open policy question:** whether a single real-world legal entity may have multiple top-level Organization aggregates across tenants, and whether those aggregates are independent operating contexts or replicas. The current source inventory cannot answer this.

## 4. Field-level system-of-record proposal

The following is a recommended starting allocation, not a claim about all existing production data.

| Information | Proposed owner | Notes / unresolved semantics |
|---|---|---|
| Canonical identity and Party kind | Party | One cross-domain identity; no tenant ownership |
| Party-facing organization classification | Ecosystem Organization profile | Define controlled taxonomy and issuer/version |
| Preferred/display name | Ecosystem Organization profile or a dedicated Party profile contract | Must distinguish public-facing name from operational label |
| Legal registered name | Legal registration evidence/authoritative registry integration; surfaced through a designated profile | Do not let two aggregates independently overwrite it |
| Registration identifier | Dedicated registration identifier/evidence contract | Namespace, issuer, jurisdiction, validity and verification required |
| Operational organization name | Top-level Organization | Tenant-local or operational label; not automatically a legal name |
| Operational lifecycle | Top-level Organization | Must not be conflated with Party ACTIVE/INACTIVE |
| Party lifecycle | Party | Canonical identity lifecycle |
| Organization units/hierarchy | Top-level Organization | Internal operational structure |
| Application access membership | Top-level Organization + Identity integration | Existing UserId semantics retained |
| Person employment/office affiliation | Workforce or dedicated Organization affiliation contract | PartyId/PersonRef semantics must be explicitly mapped |
| Tenant membership | Identity/Tenancy | Not an organization affiliation |
| Agency mandate and jurisdiction | Future Public Administration / Agency Authority capability | Never inferred from Organization membership |
| Legal registration status/evidence | Authoritative registry or legal-registration capability | Effective-dated, provenance-bearing evidence |

Where authoritative external registries are integrated, Numou should preserve the source identifier, issuing authority, jurisdiction, verification method, retrieval time and source revision. A local profile value must not be presented as externally verified without evidence.

## 5. Tenant semantics — do not decide by renaming a field

The mandatory `TenantId` on top-level Organization is an existing compatibility constraint. Its actual semantics remain unproven.

Possible meanings that must be distinguished:
1. **Creator/registration tenant:** tenant that created the operational aggregate.
2. **Owning tenant:** tenant that controls the operational aggregate.
3. **Data partition:** tenant boundary under which the aggregate and its children are stored and queried.
4. **Exclusive membership:** only tenant allowed to operate on this Organization.

These meanings are not interchangeable. The current code and inventory do not establish which one is authoritative.

**Recommendation for the next implementation phase:**
- Preserve the existing field and all current tenant checks.
- Trace create, read, update, unit, membership and event paths to identify actual semantics.
- Never weaken tenant-scoped lookups such as Courier's `TenantId + OrganizationId` contract.
- If cross-tenant participation is required, introduce an explicit participation/ownership relationship with role, status, effective period and authorization policy. Do not remove `TenantId` until all aggregate children, repository queries and API/event contracts have a safe replacement.
- Tenant participation is not government jurisdiction and is not proof of legal authority.
- A national or regional agency's ability to share data across tenants must be governed through explicit authorization and data-exchange policy, not wildcard tenant access.

## 6. Membership and affiliation semantics

Keep these relationships separate:

| Relationship | Identity | Meaning |
|---|---|---|
| Authentication principal | UserId / service principal | Can authenticate |
| Tenant membership | UserId / service principal + TenantId | Can enter a tenant context |
| Organization application access membership | UserId + OrganizationId + tenant context | Can access/act in an operational Organization context |
| Real-world person affiliation | Person PartyId (or explicitly mapped PersonRef) + OrganizationId/PartyId | Employee, officer, contractor, member or other affiliation |
| Party identity association | PartyId + issuer/subject + tenant context | External login subject represents a Party |
| Agency appointment | Person PartyId + agency/office + mandate + period | Holds a public office under a legal mandate |

The current `OrganizationMembership` is UserId-based. Preserve its behavior and do not rename or reinterpret it as employment or real-world membership. A person may be affiliated without a login; a login may have access without being an employee; an employee may have no application account.

Agency appointment is a separate, authority-bearing relationship and must include legal basis, appointing authority, scope, effective dates, revocation and audit evidence.

## 7. Identifier and contract compatibility

Do not collapse the following identifiers:
- `PartyId`: canonical person/organization identity.
- `OrganizationId`: top-level operational Organization aggregate.
- `UserId`: authentication principal.
- `TenantId`: tenant isolation context.
- `PersonRef`: existing person reference wrapper used by consumers such as Workforce.
- `OrganizationRef`: generic or capability-local reference wrappers whose target semantics need to be traced.

Before changing a reference contract:
1. Identify its owning capability and semantic target.
2. Inventory producers, consumers, API DTOs, events, serialized payloads, generated clients and persisted rows.
3. Define explicit mapping/translation at the integration boundary.
4. Preserve old contract through a compatibility window where externally consumed.
5. Add tests proving that mismatched identifiers cannot be substituted.
6. Version remote contracts and event schemas when semantics change.

A wrapper around UUID does not establish semantic equivalence. Never cast, copy or compare UUIDs as a substitute for a mapping.

## 8. Public Administration boundary

Government agency and jurisdiction semantics should be owned by a future Public Administration / Agency Authority capability, not added as flags to Party or generic Organization.

That capability should reference:
- The canonical Party where an agency is a legal organization.
- An operational Organization where it needs an application structure.
- A configurable administrative-geography hierarchy and versioned boundary references.
- Effective-dated jurisdiction assignments.
- Agency authority, legal basis, delegated powers and limitations.
- Person-to-office appointments with start/end dates and revocation.
- Evidence provenance and append-only audit.

The model must distinguish platform Tenant, Organization hierarchy and legal/geographic jurisdiction. An organization can be a service provider without being a government agency; a department may be an internal unit rather than a separate legal Party.

## 9. Implementation gates and sequence

No schema or application implementation should begin until the following evidence is collected.

### Gate A — lifecycle and field authority
- Trace all create/update/read flows for both Organization models.
- Identify runtime CDI/provider wiring and actual persistence adapters.
- Determine whether the same real-world organization is represented in both models in fixtures, seed data or production.
- Assign system-of-record and synchronization direction per overlapping field.
- Decide whether and how multiple operational Organizations may map to one Party.

### Gate B — tenant and authorization semantics
- Trace TenantId creation, ownership, queries, child records and events.
- Identify cross-tenant organization use cases and current policy.
- Verify no tenant membership is treated as agency authority.
- Define authorization for association creation/correction and cross-tenant reads.

### Gate C — reference contract inventory
- Complete repository-wide source scan for OrganizationId, PartyId, PersonRef, OrganizationRef, raw organization UUIDs and related serialized contracts.
- Inspect generated clients, API schemas, event payloads, tests, seed data and migrations.
- Trace the Tenancy, Workforce, Courier and Construction references called out by M1.0a.
- Record external integration and production-data owners.

### Gate D — persistence and transaction design
- Define production persistence for top-level Organization and the Ecosystem profile independently.
- Specify transaction boundaries, optimistic concurrency, uniqueness, idempotency, outbox behavior and audit.
- Define migration/rollback and dual-read/dual-write only if truly required; avoid permanent dual-write as a default.
- Add tenant-isolation and authorization negative tests.

### Gate E — implementation proposal and approval
Only after Gates A–D, prepare a code-level M1.0c contract/persistence plan with:
- Exact module and package ownership.
- Public ports and adapters.
- Table and index design.
- Migration phases and rollback.
- API/event compatibility.
- Test matrix and reactor commands.
- Operational and observability requirements.

No destructive migration, identifier rewrite, profile deletion, tenant semantic change or PR is authorized by this document.

## 10. Acceptance criteria for M1.0b

- The two Organization models have distinct documented meanings.
- Party remains the canonical identity and no UUID equality mapping is assumed.
- No universal OrganizationId↔PartyId mapping is mandated.
- UserId access membership is distinct from person affiliation, tenant membership and agency appointment.
- Tenant ownership/participation is explicitly unresolved pending source and use-case evidence; current tenant checks remain protected.
- Public agency authority and jurisdiction are kept outside generic Party and Organization profile fields.
- M1.0's premature profile-retirement assumption is explicitly superseded.
- The next milestone is evidence gathering and a code-level contract plan, not speculative scaffolding.

## 11. Open decisions requiring product-owner confirmation

1. May one legal Organization Party have multiple operational Organization aggregates, for example one per tenant or operating context?
2. Should the Ecosystem Organization profile remain the canonical home for Party-facing organization type and display name, or should those fields move to a separately named Party profile capability?
3. What does the top-level Organization's mandatory TenantId mean in current product behavior: creator, owner, data partition or exclusive tenant?
4. Should top-level Organization support non-legal operational structures such as departments, teams and project consortia, or should some of these remain OrganizationUnit/other aggregates?
5. Which external registries are authoritative for legal name and registration status in the initial Indonesia deployment?

These are product/domain policy questions. The recommended defaults in this document preserve existing contracts and avoid irreversible assumptions until they are answered.
