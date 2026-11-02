# Public-Sector Platform — M1.0c Canonical Organization-to-Party Association Contract

**Status:** Proposed contract / design gate  
**Branch:** `feat/public-sector-platform-m1-0a-inventory`  
**Depends on:** M1.0a source inventory and M1.0b tenant/membership semantics  
**Scope:** Define how the Organization aggregate is explicitly associated with canonical Ecosystem Party identity.  
**Change policy:** Contract only. No Java code, schema, migration, API, event payload, runtime wiring or production data is changed.

## 1. Decision

The top-level Organization capability remains the owner of the Organization aggregate and its organizational structure. Ecosystem Party remains the canonical identity owner.

An Organization that represents a legal/recognized organization Party may carry one explicit, validated association:

`OrganizationId ↔ PartyId`

The association is a first-class relationship with its own lifecycle, verification evidence and audit history. It is not an ID conversion.

- `OrganizationId` remains the stable identifier for the Organization aggregate, units and existing OrganizationMembership contracts.
- `PartyId` remains the canonical cross-domain identity.
- The UUID values are independent even though both wrappers currently contain UUID.
- A mapping is created only after the referenced Party is confirmed to exist, be active and have `PartyKind.ORGANIZATION`.
- No consumer may infer an association from UUID equality, matching names, registration numbers or tenant IDs.
- A mapping does not grant tenant access, organization membership, employment, public authority, data jurisdiction or authorization.
- The Ecosystem Organization profile must not become a second source of organizational structure. Its profile data should ultimately be reconciled into the Organization capability through a separately approved migration.

This contract does not authorize implementation until the gates in Section 11 are met.

## 2. Ownership and dependency direction

| Concern | Owner | Contract boundary |
|---|---|---|
| Canonical Party identity, PartyId, kind and lifecycle | Ecosystem / Party | Public Party lookup/validation application port |
| Organization aggregate and OrganizationId | Organization | Organization domain and repository |
| Organization↔Party association lifecycle | Organization | Organization domain/application contract, with Party validation through a port |
| Tenant membership and tenant isolation | Tenancy / Security | Existing tenant membership and authorization contracts |
| User login and authenticated subject | Identity | Existing identity/authentication contracts |
| Legal registration evidence | Organization capability, jurisdiction-aware records | Separate registration record; not the association key |

Dependency direction:
- Organization application code may depend on a stable Party identity validation port.
- Organization domain code must not depend on Ecosystem persistence, JDBC classes, Party repository implementation or Identity internals.
- Ecosystem must not write Organization tables or own Organization aggregate transitions.
- Persistence adapters may implement ports, but cross-capability SQL table access is not a substitute for application contracts.
- A modular monolith may compose the Party lookup and Organization repository in one transaction only when both adapters share a real transaction boundary.
- A distributed deployment must use a versioned request/response contract or a durable workflow; it must not pretend a network call participates in a local database transaction.

## 3. Relationship cardinality and invariants

The target active mapping is one-to-one:

1. An OrganizationId has at most one active canonical PartyId association.
2. A PartyId of kind ORGANIZATION has at most one active OrganizationId association in this capability.
3. An active association must reference an existing, active Party of kind ORGANIZATION.
4. An association is global identity linkage, not tenant-scoped identity. Tenant-scoped authorization and operational relationships remain separate.
5. An Organization aggregate may exist before it has a Party association during a compatibility period, but new public-sector onboarding workflows should require validated association before exposing it as a canonical organization.
6. A Party may exist without an Organization aggregate (for example, an organization Party referenced by another capability or an imported identity not yet onboarded to Organization management).
7. A Party of kind PERSON or UNKNOWN must never be associated with an Organization aggregate.
8. Association does not merge, overwrite or synchronize all attributes between Party and Organization. Each capability retains its owned facts.

If future evidence demonstrates that one Party must represent multiple separately managed Organization aggregates, do not weaken uniqueness ad hoc. Introduce a separately named and semantically defined relationship (for example, branch, legal entity, operating unit or representation) with explicit cardinality and lifecycle. Do not overload the canonical identity association.

## 4. Proposed association contract

The following is a logical contract, not a committed Java API or database schema.

### 4.1 Identity and evidence

An association record should contain:

| Field | Meaning |
|---|---|
| associationId | Stable identifier for the relationship record |
| organizationId | Typed Organization aggregate identifier |
| partyId | Typed canonical Party identifier |
| status | Pending verification, active, suspended or superseded/revoked lifecycle |
| verificationMethod | How the identity relationship was established (authoritative registry, authorized operator review, trusted migration evidence, etc.) |
| evidenceReferences | References to authoritative evidence; avoid storing unnecessary sensitive document contents in the association row |
| verifiedAt | Time the mapping was verified |
| verifiedBy | Authenticated actor or trusted system identity responsible for verification |
| effectiveFrom / effectiveUntil | Business validity interval where the source process requires temporal validity |
| createdAt / updatedAt | Technical audit timestamps |
| correlationId / causationId | Traceability for commands and events |
| version | Optimistic concurrency/version token |

Do not store a free-text statement as the only evidence. Verification method and evidence type should be governed values, while evidence references point to controlled records with their own access policies.

### 4.2 Lifecycle

Proposed lifecycle:

- `PENDING_VERIFICATION`: candidate exists but cannot be used as a canonical association.
- `ACTIVE`: all invariants have been verified; this is the only state that resolves OrganizationId to PartyId for normal business operations.
- `SUSPENDED`: mapping is under investigation or temporarily not trusted; resolution must fail closed for operations requiring canonical linkage.
- `SUPERSEDED`: mapping was replaced through an audited correction process. Retained for history and never silently reactivated.
- `REVOKED`: mapping was established in error or legally/operationally withdrawn; retained for history and not reusable.

A pending candidate must not reserve canonical identity indefinitely unless a separately specified workflow requires a short-lived reservation. If reservation is introduced, it must expire and be concurrency-safe.

Transitions must be explicit commands with actor, reason and evidence:
- pending → active after Party validation and verification;
- pending → revoked when rejected;
- active → suspended for investigation;
- suspended → active only after re-verification;
- active/suspended → superseded when a correction installs a replacement;
- active/suspended → revoked when the association is invalidated.

No direct mutation of `partyId` on an active association. A correction creates a new association record and closes the old one atomically where a local transaction is available, or through a durable, observable workflow when distributed.

## 5. Party validation contract

Organization application layer needs a capability-neutral outbound port with semantics equivalent to:

- lookup Party by PartyId;
- return canonical PartyId, PartyKind and lifecycle status;
- distinguish not-found, kind mismatch, inactive/blocked identity and unavailable/indeterminate dependency;
- carry request correlation and caller/tenant context needed for authorization and audit;
- never return private Party attributes that Organization does not need.

Validation outcomes:

| Outcome | Association behavior |
|---|---|
| Party not found | Reject; no association write |
| Party kind is PERSON or UNKNOWN | Reject; no association write |
| Party inactive/blocked | Reject or require an explicitly governed exception; never silently activate |
| Party active and kind ORGANIZATION | Continue to evidence and uniqueness validation |
| Party service unavailable / timeout | Fail closed or leave a non-resolving pending workflow; never treat unavailable as valid |
| Caller not authorized to establish link | Reject and audit; do not reveal unnecessary Party details |

A Party lookup is not itself proof that the caller may associate that Party with this Organization. The command must separately authorize the actor for the Organization and the association operation. Where a public-sector registry or legal evidence is required, the Party lookup must be combined with that evidence.

### 5.1 Local composition

In a modular monolith, the composition root may bind the port to the Ecosystem Party application service. If Party and Organization writes are required to be atomic, they must share a transaction manager and database transaction; do not claim atomicity when they do not.

### 5.2 Distributed composition

In a distributed deployment:
- use a versioned Party validation endpoint or contract;
- define timeout, retry, idempotency and unavailable behavior;
- record the validation result and its time/version/provenance;
- use a durable command workflow and outbox/inbox for reliable completion;
- revalidate before activation if the pending period exceeds the defined freshness window;
- handle Party deactivation or correction through a governed event/notification contract and reconciliation process.

Do not synchronously call a remote Party service from inside an open database transaction and assume the remote service participates in that transaction.

## 6. Tenant and authorization semantics

The association itself is not tenant-scoped. Canonical identity is not owned by the tenant that first encounters it.

However, commands to create, inspect or change an association must be authorized in context:
- identify the caller and active tenant context;
- verify that the caller may administer the Organization in that tenant;
- verify any separate permission to link canonical Party identity;
- validate legal/registry evidence required by jurisdiction;
- audit the action and its reason.

TenantId must not be added to the association as an ownership key merely because the existing Organization aggregate currently requires TenantId. That aggregate constraint is a separate compatibility issue. A tenant's OrganizationRef is not assumed to be either OrganizationId or PartyId until its consumers and semantics are proven.

Cross-tenant behavior:
- the same canonical Party identity may be visible in multiple tenant contexts only through explicitly authorized, purpose-bound access;
- an active global Organization↔Party mapping does not allow one tenant to read another tenant's Organization data;
- no association lookup may bypass tenant filters for Organization-owned records;
- data sharing between government agencies requires a separate lawful purpose, jurisdiction and data-sharing authorization contract.

## 7. Uniqueness, concurrency and correction

The persistence implementation must enforce uniqueness under concurrency, not rely only on application pre-checks.

Target invariants for the active association:
- unique active `organizationId`;
- unique active `partyId`;
- foreign-key/reference integrity to the persisted Organization aggregate where both are in the same persistence boundary;
- Party existence/kind validation through the Party contract where a cross-database foreign key is not possible;
- optimistic concurrency for lifecycle transitions;
- idempotent command handling using a stable idempotency key and request fingerprint.

Duplicate outcomes must be deterministic:
- same OrganizationId + same PartyId + same active association: return idempotent success only when command identity/fingerprint and authorization rules permit;
- same OrganizationId + different PartyId: conflict requiring explicit correction workflow;
- same PartyId + different OrganizationId: conflict; do not create a second active link;
- concurrent attempts: one wins; the other receives a stable conflict result after re-reading committed state.

Correction is not a normal update:
1. open a correction case with reason and evidence;
2. suspend the disputed association if necessary;
3. verify the replacement Party and authority of the actor;
4. atomically supersede/revoke the old association and activate the replacement where possible;
5. publish an audit/domain event through an outbox;
6. reconcile downstream projections and caches;
7. preserve old mapping and its evidence for the required retention period.

Never delete historical associations to make uniqueness checks pass.

## 8. Events and integration contracts

Potential domain events, subject to the repository's existing event conventions and versioning policy:

- `OrganizationPartyAssociationProposed`
- `OrganizationPartyAssociationActivated`
- `OrganizationPartyAssociationSuspended`
- `OrganizationPartyAssociationSuperseded`
- `OrganizationPartyAssociationRevoked`

Events should contain stable identifiers, association version, lifecycle transition, effective time, tenant/caller context where legally appropriate, correlation/causation identifiers and references to controlled evidence. They should not contain unnecessary personal data, identity documents, full registry records or authentication secrets.

Consumers must:
- treat events as notifications of committed state, not authorization tokens;
- be idempotent by event ID and aggregate/version;
- tolerate duplicate and out-of-order delivery;
- fetch current authorized state when necessary;
- support schema evolution and unknown optional fields;
- never infer Party kind or tenant authorization solely from an event payload.

The event names above are proposals, not claims that these event types already exist.

## 9. Query contract

Read contracts should be explicit and authorization-aware:

- resolve active PartyId for an OrganizationId;
- resolve active OrganizationId for a PartyId;
- retrieve association history for authorized audit/reconciliation use.

Normal resolution returns only ACTIVE mappings. Pending, suspended, superseded and revoked states require separate privileged history/audit queries. A not-found result must not reveal whether an inaccessible Organization or Party exists in another tenant.

A read model or cache must be rebuildable from authoritative association state/events and must not become a second source of truth.

## 10. Migration and compatibility rules

1. Keep existing OrganizationId-based repository, unit and OrganizationMembership contracts stable during the first migration stage.
2. Add the association alongside existing identifiers; do not replace OrganizationId in aggregate keys, foreign keys, API paths, event payloads or tenant references as part of this milestone.
3. Do not automatically associate records using equal UUID values, name similarity, registration-number similarity or shared TenantId.
4. Treat existing Ecosystem Organization profiles as candidate evidence only. Reconcile each profile to an Organization aggregate through explicit, reviewable matching; do not assume every profile has a corresponding aggregate.
5. Do not silently copy legal/display names or status between the two models. Define ownership and conflict resolution per field before migration.
6. Preserve old APIs and event consumers; introduce versioned contracts or additive optional fields only after consumer inventory.
7. Backfill requires a dry-run report, source provenance, deterministic conflict classification, human review for ambiguous matches, idempotent batches, checkpoints, rollback/reconciliation plan and post-migration verification.
8. If the mapping service or Party lookup is unavailable during migration, stop or leave the record unresolved; do not synthesize an identity.

## 11. Gates before implementation (M1.0d+)

The following evidence is required before adding Java contracts or persistence:

- [ ] Complete OrganizationId, PartyId, OrganizationRef and LegalEntityRef consumer inventory across API, events, persistence, fixtures and generated clients.
- [ ] Inspect all Ecosystem persistence Java sources and runtime wiring to establish whether Organization profile persistence exists outside the inspected migrations.
- [ ] Verify canonical Party repository, persistence transaction and lifecycle contracts end-to-end.
- [ ] Verify Organization aggregate persistence, transaction, event publication and rehydration strategy; current inspected Organization adapters are in-memory.
- [ ] Resolve whether an Organization aggregate may participate in multiple tenants and what the current mandatory TenantId means.
- [ ] Establish whether one Party may legitimately map to multiple Organization aggregates for any supported use case; if yes, specify the distinct relationship semantics before changing cardinality.
- [ ] Approve verification evidence types and authorized actors for Indonesia's initial jurisdiction.
- [ ] Define event delivery and idempotency conventions against existing Numou outbox/inbox capabilities.
- [ ] Review data protection, retention, correction and audit requirements with the relevant domain/legal owners.

## 12. Acceptance criteria

- [x] Preserve OrganizationId and PartyId as distinct typed identifiers.
- [x] Define ownership and dependency direction.
- [x] Define active association cardinality and lifecycle.
- [x] Require Party existence, active status and ORGANIZATION kind before activation.
- [x] Separate identity association from tenant membership, affiliation and authorization.
- [x] Define local and distributed validation behavior without false transaction guarantees.
- [x] Define concurrency, idempotency, correction and historical retention expectations.
- [x] Define proposed events and authorized query behavior without claiming implementation exists.
- [x] Define compatibility and migration safeguards.
- [ ] Close all implementation gates in Section 11.
- [ ] Approve the contract with Organization, Ecosystem, Tenancy, Security and public-sector domain owners.

## 13. Next milestone

M1.0d should verify production persistence and transaction patterns before code is introduced. It must establish whether Organization and Party can be written atomically in each supported composition, how their outboxes work, and what durable storage implementations actually exist. Only then should M1.0e introduce additive, tested application contracts and persistence.
