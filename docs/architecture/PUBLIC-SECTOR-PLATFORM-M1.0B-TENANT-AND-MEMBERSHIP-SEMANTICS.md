# Public-Sector Platform — M1.0b Tenant and Membership Semantics

**Status:** Design contract / implementation gate  
**Branch:** `feat/public-sector-platform-m1-0a-inventory`  
**Depends on:** M1.0a Organization/Party source inventory  
**Scope:** Define the distinct meanings and invariants for canonical identity, tenant association, account membership, organization affiliation and public authority appointments.  
**Change policy:** No application code, schema, migration, API, event payload or production data is changed by this milestone.

## 1. Decision summary

M1.0b establishes that Numou must not use one relationship or identifier to represent multiple kinds of belonging.

1. **Party identity is global canonical identity.** A Party represents a person or organization and is not itself a tenant, account, organization membership or permission grant.
2. **Tenant is the isolation and operational boundary.** Tenant membership is a relationship between an authenticated User account and a Tenant. It does not prove that the user is an employee, officer, owner or legal representative of an Organization.
3. **Organization is the legal/operational organization aggregate.** Its stable aggregate identity remains OrganizationId. It may be explicitly associated with a canonical organization Party, but the two IDs must never be inferred equal.
4. **Organization account membership remains access-oriented.** Existing OrganizationMembership (OrganizationId + UserId) is not a person affiliation record and must not be silently converted to PartyId.
5. **Person-to-organization affiliation is a separate domain relationship.** It should identify PartyId + OrganizationId and carry its own role/type, effective period, status, provenance and verification where required.
6. **Public office and government authority are separate from employment and membership.** An appointment must be modeled as a mandate/appointment with jurisdiction, office, authority scope, effective period, appointing authority and revocation evidence. A person being a tenant member or organization member grants no public power.
7. **Tenant ownership, data stewardship and jurisdiction are separate dimensions.** A tenant may operate a system or hold a data partition without becoming the legal owner of every Party or Organization represented in it.

These are domain semantics and safety constraints. They do not assert that the repository already implements all corresponding relationships.

## 2. Existing contract evidence

| Existing source | What it means today | What it must not be interpreted as |
|---|---|---|
| `ecosystem.domain.party.PartyId` and `Party` | Canonical person/organization identity | Tenant membership or login account |
| `organization.domain.OrganizationId` and `Organization` | Organization aggregate identity; aggregate currently requires TenantId | PartyId, tenant ID, or an authenticated principal |
| `organization.domain.OrganizationMembership` | OrganizationId + UserId membership lifecycle | Party affiliation, employment proof or authorization role |
| `tenancy.domain.TenantMembership` | TenantId + UserRef relationship; one user may join multiple tenants | Organization affiliation or authorization policy |
| `tenancy.domain.TenantMemberType` | Nature of tenant membership (OWNER, ADMIN, MEMBER, OPERATOR, EXTERNAL, SERVICE) | Fine-grained business permissions or legal office |
| `foundation.domain.ref.OrganizationRef` | Lightweight UUID reference used by Tenant aggregate | Proven OrganizationId or PartyId until its contract is explicitly bound |
| `ecosystem.domain.PartyIdentityAssociation` | Tenant-scoped external issuer/subject ↔ Party link | Tenant membership, Organization membership or permission grant |
| `ecosystem.domain.PartyParticipantAssociation` | Tenant-scoped Party ↔ Participant business relationship | Employment, public office or authorization |
| `security.spi.TenantMembershipResolver` | Checks active membership of a Principal in a Tenant | Proof of legal relationship with an Organization |

### 2.1 Tenant's OrganizationRef requires an explicit contract

The Tenant aggregate stores an optional Foundation `OrganizationRef`, and its `changeOrganization` operation changes that reference. The reference is a generic UUID wrapper; its source does not identify whether it points to the top-level OrganizationId, canonical PartyId, or another organization identity contract.

Until a typed contract and actual consumer evidence are established:
- do not reinterpret persisted OrganizationRef UUIDs as PartyIds;
- do not replace OrganizationRef with PartyId or OrganizationId in-place;
- do not make tenant creation require an Organization;
- do not infer that the referenced organization owns the tenant's data;
- do not add cross-tenant read access because two tenants reference the same organization.

## 3. Canonical relationship model

The target model keeps the following relationships independent.

| Relationship | Subject | Target | Purpose | Authority granted |
|---|---|---|---|---|
| Authentication link | External issuer + subject | PartyId, tenant-scoped | Resolve an external identity to canonical Party | None by itself |
| Account membership | UserId | TenantId | Permit the account to enter a tenant context | Only the access established by separate authorization policy |
| Organization access membership | UserId | OrganizationId | Associate an account with an organization workspace/administrative boundary | Only explicit organization-scoped permissions |
| Person affiliation | Person PartyId | OrganizationId | Record employment, contractor, volunteer, board, resident, or other relationship | No login permission by itself |
| Organization identity association | OrganizationId | Organization PartyId | Connect an Organization aggregate to canonical identity | No tenant access by itself |
| Tenant organization reference | TenantId | Explicitly typed organization reference | Identify the tenant's declared organization relationship | No data ownership or jurisdiction inferred |
| Government appointment | Person PartyId | Office + jurisdiction + appointing authority | Record public authority with a defined mandate | Only the authority scope valid during its effective period |
| Data stewardship | Steward organization/agency | Dataset or data domain | Define operational responsibility, quality and lawful processing duties | No general ownership of the represented people or property |

A single Party may have many affiliations and appointments over time. A single Organization may be represented in multiple tenant contexts. Those facts do not permit cross-tenant access to records.

## 4. Tenant isolation and government data ownership

For the public-sector platform, distinguish at least:

- **Tenant boundary:** technical isolation, tenant-scoped membership, tenant context and storage access.
- **Jurisdiction:** geographic/legal scope such as national, provincial, regency/city, district or village; jurisdiction determines which authority may act, not which tenant can query arbitrary records.
- **Data controller / responsible agency:** legally accountable entity for a processing purpose under applicable law.
- **Data steward / custodian:** operational responsibility for data quality, source lineage, correction workflow and controlled access.
- **Source authority:** authoritative registry or institution that originated a fact.
- **Data subject / represented entity:** person, household, organization, parcel, event or other subject described by a record.

A tenant must not become a universal data-ownership field. A government agency's membership in a tenant does not establish its jurisdiction over every citizen or land record.

Cross-agency data sharing must use explicit, purpose-bound authorization and a governed exchange contract. It must not be implemented by removing tenant predicates or making Party globally queryable as a proxy for all Party-linked data.

## 5. Relationship lifecycle and evidence requirements

Affiliation and appointment records must be temporal and auditable. A future implementation should include, as appropriate:

- stable relationship identifier;
- typed subject and target identifiers;
- relationship type or office code from a governed vocabulary;
- status and effective-from/effective-until timestamps;
- source/provenance and verification status;
- issuing or appointing authority;
- jurisdiction and scope;
- recorded-by actor, correlation and reason;
- append-only history for revocation, correction and supersession;
- explicit handling of overlapping, concurrent and expired relationships.

Do not store mutable free-text roles as the sole source of authorization. Do not infer current status solely from an end date if a revocation or correction record is required by the domain.

## 6. Authorization rules

- Authentication proves control of an account or external subject; it does not prove an employment or legal relationship.
- Tenant membership is necessary for tenant access where required, but it is not sufficient for every operation.
- Organization access membership is not equivalent to person affiliation.
- Affiliation is not an authorization grant.
- A public office appointment is not a blanket role; authorization must evaluate mandate, scope, jurisdiction, effective time, action and resource.
- Possession of PartyId, OrganizationId, TenantId or a relationship ID never grants access.
- All reads and writes of tenant-owned records must enforce tenant scope independently of canonical identity resolution.
- Emergency/break-glass access, if required, must be separately authorized, time-bound, reason-coded, audited and reviewable.

## 7. Compatibility rules for convergence

1. Preserve existing `OrganizationId`, `UserId`, `TenantId`, `PartyId` and `OrganizationRef` typed contracts until a versioned compatibility plan is approved.
2. Preserve existing OrganizationMembership as account membership; do not rename its semantics by changing its key to PartyId.
3. Introduce person affiliation as a new, separately owned relationship contract only after confirming the owning capability and its public application ports.
4. Introduce OrganizationId ↔ PartyId association as an explicit verified mapping, with provenance and lifecycle, only after canonical Party persistence and Organization persistence are both available.
5. Keep tenant participation separate from the Organization↔Party association. A Party may be global while tenant-scoped relationships and data remain isolated.
6. No backfill may use UUID equality, name similarity or registration-number similarity as automatic proof of identity. Candidate matching requires an authoritative source and a reviewable reconciliation process.
7. Preserve old and new read paths during migration; use dual-write only when transaction/outbox semantics and reconciliation are proven.

## 8. Open decisions — intentionally not guessed

The following require product/legal/operational evidence and are not resolved by source inspection:

- Can one Organization aggregate participate in multiple tenants, or is the current mandatory TenantId a strict ownership boundary?
- Does Tenant.organization currently mean legal owner, tenant sponsor, primary organization, or another concept?
- Can a tenant exist without a legal organization (for example, government shared service, individual operator, sandbox or platform-managed tenant)?
- Which Organization relationships are needed for each public-sector workflow: employment, civil service, elected office, contractor, resident, guardian, household membership, board appointment, agency delegation?
- Which jurisdiction hierarchy and legal authority sources are authoritative for Indonesia and future jurisdictions?
- Which agency is data controller, steward, custodian and source authority for each dataset?
- Which external identity providers and national identity services are trusted, and what are their lawful linkage rules?
- Which existing deployed schemas, integrations and production data depend on OrganizationRef and OrganizationId?

These are explicit gates for later implementation, not reasons to add generic fields prematurely.

## 9. Acceptance criteria for M1.0b

- [x] Distinguish Party identity from User account identity.
- [x] Distinguish Tenant membership from Organization membership.
- [x] Distinguish Organization membership from person affiliation.
- [x] Distinguish affiliation from public office appointment and authorization.
- [x] Distinguish tenant isolation from legal jurisdiction and data stewardship.
- [x] Record OrganizationRef as unresolved generic reference pending typed consumer evidence.
- [x] Define compatibility rules prohibiting UUID-based identity inference.
- [ ] Obtain domain/legal owner decisions for the open questions in Section 8.
- [ ] Validate the semantics against every discovered OrganizationRef, OrganizationId and Tenant membership consumer.
- [ ] Approve the target relationship contracts before M1.0c API design or code changes.

## 10. Implementation gate

M1.0b provides a safe semantic baseline, not permission to change application behavior. M1.0c may proceed to contract design only after the open questions and consumer dependencies are reviewed. No new tables, aggregate fields, endpoints or events are introduced by this document.
