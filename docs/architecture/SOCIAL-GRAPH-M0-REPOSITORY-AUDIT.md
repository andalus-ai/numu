# Social Graph M0 — Repository Audit Findings

- **Status:** M0 audit findings (source-tree review); implementation and runtime verification remain pending
- **Date:** 2026-11-03
- **Repository:** `andalus-ai/numu`
- **Branch:** `social-graph/m0-foundation-architecture`
- **Related:** [Social Graph Foundation Architecture](SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md), [ADR-003](ADR-003-reusable-capability-modules.md), [PersonRef/PartyId mapping decision](person-party-identity-mapping-decision.md), [Organization/Party convergence](organization-party-identity-convergence.md), [Customer capability contract](customer-capability-contract.md)

## 1. Executive summary

The repository already contains useful identity and relationship building blocks, but it does not contain a dedicated Social Graph capability on the audited `main` tree.

The most important finding is that the term "relationship" is already used for several different business concepts. These must not be collapsed into one universal relationship aggregate:

1. `PartyRelationship` is a directed relationship between canonical Parties, with a small commercial/organizational type vocabulary.
2. `ParticipantRelationship` is a relationship between Ecosystem Participants, intended for provider/customer/supplier/carrier commercial or operational links.
3. `PartyParticipantAssociation` links a canonical Party to an Ecosystem Participant with a tenant-scoped business role.
4. `OrganizationPartyAssociation` explicitly links the top-level Organization aggregate to canonical Ecosystem Party identity.
5. `OrganizationMembership` links an Organization to an authenticated `UserId`; it is not the same as a social connection or a Party affiliation.

**M0 recommendation:** Do not create Social Graph by extending or renaming any one of these models. First define a dedicated social relationship contract over canonical Party references, and explicitly integrate with existing contracts through public ports.

PostgreSQL remains the right initial authoritative store. The audited source tree does not establish a persisted PartyRelationship repository/adapter or an existing Neo4j graph adapter. Neither a Neo4j runtime nor graph projection infrastructure should be added in M1.

## 2. Repository instructions and build topology

### Normative contributor guidance inspected

- `AGENTS.md` requires reading architecture ownership documents, inspecting real Maven reactor roots, avoiding duplicate invariants, keeping domain code infrastructure-neutral, preserving tenant/security contracts, reporting test evidence accurately, and not opening a PR without explicit user approval.
- `docs/ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md` requires Numou to remain application-neutral, preserve stable contracts, and prioritize identity, tenancy, security and production quality.
- `docs/architecture/ADR-003-reusable-capability-modules.md` confirms there is no repository-root Maven POM and that the repository intentionally contains multiple Maven reactor roots. The shared framework parent is `core/framework/pom.xml`.

### Observed reactor roots

The root tree includes (among others):

- `core/framework` — shared framework and Ecosystem.
- `identity` — identity/authentication.
- `organization` — organization structure and membership.
- `workforce` — workforce/HRIS.
- `crm` — CRM.
- `geo`, `mobility`, `logistics`, `commerce`, `workspace`, and other independent capability roots.

Inspected aggregator POMs confirm:
- `core/framework/ecosystem/pom.xml` aggregates `ecosystem-domain`, `ecosystem-application`, and `ecosystem-persistence-jdbc`.
- `identity/pom.xml` aggregates identity domain, application, and adapter.
- `organization/pom.xml` aggregates organization domain, SPI, application, and adapter.
- `workforce/pom.xml` aggregates workforce domain, SPI, application, adapter, JDBC persistence, and HRIS.

Do not run or document `mvn` at repository root as the build command. M0 has not run Maven builds in this audit session.

## 3. Existing relationship and identity inventory

### 3.1 Canonical Ecosystem Party

Observed source:
- `core/framework/ecosystem/ecosystem-domain/.../domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyId.java`
- `core/framework/ecosystem/ecosystem-domain/.../domain/party/PartyRef.java`
- `core/framework/ecosystem/ecosystem-application/.../port/PartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/.../db/migration/V2__canonical_parties.sql`

Findings:
- Party is explicitly documented as the canonical cross-domain identity for a person or organization.
- Party kind is `PERSON` or `ORGANIZATION`; Party owns identity kind and lifecycle, not names, contact details, organization structure, or person-specific attributes.
- Party IDs are UUID-backed and globally unique in the current `ecosystem_party` table; the migration comment says Party identity is cross-tenant.
- `PartyRepository` and `JdbcPartyRepository` exist, and the JDBC adapter persists Party identity/lifecycle.
- Party persistence is not tenant-owned; tenant-specific identity associations are separate.
- `PartyRef` carries a PartyId and optional kind hint.

Implication:
- Social Graph subject identity should reference `PartyId` for people and organizations once the caller has resolved the subject through the appropriate public contract.
- A graph edge still needs an explicit visibility/tenant access policy. Global identity does not imply globally visible relationships or profile data.

### 3.2 Party-to-authenticated-identity association

Observed source:
- `IdentitySubjectRef.java`
- `PartyIdentityAssociation.java`
- `PartyIdentityAssociationRepository.java`
- `JdbcPartyIdentityAssociationRepository.java`
- `V1__party_identity_associations.sql`

Findings:
- Party-to-authenticated-subject linkage is explicit rather than inferred from matching UUIDs.
- Repository contract includes tenant-scoped lookup by tenant and identity subject, and lookup by tenant and Party.
- Persistence enforces tenant-scoped active identity association uniqueness.

Implication:
- Authentication principal/UserId must not be treated as a social graph PartyId without resolving this explicit association.
- Social Graph commands should receive an authenticated actor context and resolve the actor's authorized Party association through a public application port.

### 3.3 PartyRelationship

Observed source:
- `PartyRelationship.java`
- `PartyRelationshipType.java`
- `PartyRelationshipStatus.java`
- `PartyRelationshipTest.java`

Findings:
- PartyRelationship is directed from source Party to target Party.
- It rejects self-relationships and has effective-from/effective-to dates.
- Its current type enum is `CUSTOMER_OF`, `SUPPLIER_OF`, `PARTNER_OF`, `PROVIDER_OF`, `MEMBER_OF`, and `SUBSIDIARY_OF`.
- It defaults to PROPOSED on construction and `establish` creates an ACTIVE relationship.
- Termination is modeled; this type does not expose a connection-request/acceptance lifecycle, follow semantics, block semantics, mute semantics, or social privacy rules.
- In the audited recursive source tree, no `PartyRelationshipRepository`, JDBC persistence adapter, or PartyRelationship migration was found. The existing domain model therefore must not be described as a complete persisted relationship service.

Implication:
- Do not reuse `PartyRelationshipType` for LinkedIn-style connections, follows or blocks.
- Do not add social types to this enum: commercial/legal/organizational relationships and social interactions have different invariants and lifecycles.
- M1 must not assume PartyRelationship is already a durable persistence contract.

### 3.4 ParticipantRelationship

Observed source:
- `ecosystem-domain/.../domain/model/ParticipantRelationship.java`
- `ecosystem-domain/.../domain/valueobject/RelationshipType.java`
- `ecosystem-application/.../port/ParticipantRelationshipRepository.java`

Findings:
- This model connects Ecosystem Participants, not canonical Party identities.
- Its own documentation describes commercial/operational links such as who trades with whom and provider/customer/supplier/carrier relationships.
- It has PROPOSED, ACTIVE, SUSPENDED and TERMINATED lifecycle semantics and effective dates.
- A repository port exists with save/find-by-id/find-outgoing/find-incoming methods.
- The audit did not find a corresponding production persistence implementation for this port in the Ecosystem JDBC source tree.

Implication:
- ParticipantRelationship is not the Social Graph's person-to-person connection model.
- Social Graph may later reference participant/provider relationships for business network discovery, but should not silently reinterpret Participant IDs as Party IDs.

### 3.5 PartyParticipantAssociation

Observed source:
- `PartyParticipantAssociation.java`
- `PartyParticipantAssociationRepository.java`
- `V3__party_participant_associations.sql`

Findings:
- This is a tenant-scoped association between canonical Party and Ecosystem Participant, with roles such as CUSTOMER, PARTNER, SUPPLIER, SERVICE_PROVIDER, MEMBER, EMPLOYEE and OTHER.
- The migration enforces active uniqueness by tenant, Party, Participant and role.
- It is a business role/association, not a mutual social connection.

Implication:
- Reuse the public association contract to enrich business-network context where authorized.
- Do not copy these role facts into generic Social Graph edges.

### 3.6 Organization identity and membership

Observed source:
- `organization-domain/.../Organization.java`
- `OrganizationId.java`
- `OrganizationMembership.java`
- `OrganizationPartyAssociation.java`
- `OrganizationPartyAssociationRepository.java`
- `JdbcOrganizationPartyAssociationRepository.java`

Findings:
- Top-level Organization is tenant-owned and keyed by `OrganizationId`.
- Organization membership currently references `UserId`, not PartyId.
- OrganizationPartyAssociation is an explicit, audited association between OrganizationId and canonical PartyId. Its own documentation states that the identifiers remain distinct.
- The association supports pending verification, activation, suspension, reinstatement, supersession and revocation, and stores actor/evidence/reason metadata.
- Repository contracts support current association lookup by OrganizationId or PartyId and history lookup.

Implication:
- A graph organization subject should use canonical PartyId after resolving the explicit OrganizationPartyAssociation; do not cast OrganizationId to PartyId.
- Organization membership is not automatically a social connection, and a UserId is not automatically a PartyId.
- Membership visibility and authorization remain owned by Organization/Identity policy.

### 3.7 PersonRef and Workforce

Observed source:
- `core/framework/foundation/numou-foundation-domain/.../domain/ref/PersonRef.java`
- `docs/architecture/person-party-identity-mapping-decision.md`
- `workforce/README.md` and Workforce reactor.

Findings:
- PersonRef is a UUID-backed universal person reference.
- PartyId is a separate UUID-backed canonical Party identifier.
- Existing architecture decision explicitly prohibits implicit UUID aliasing and calls for an explicit validated mapping during migration.
- The source-tree audit has not established that every persisted PersonRef has a matching Party record.

Implication:
- Do not accept PersonRef as interchangeable with PartyId in graph contracts.
- M0 identity mapping remains a gate for any Workforce/Person integration. A named resolver/translation contract must validate existence, PartyKind.PERSON and access policy.

### 3.8 Dedicated social/profile/community implementation

The audited root directory list contains no top-level `social` or `community` capability root, and the recursive source-tree search did not find a dedicated Social Graph reactor. This does not mean there are no profile-like concepts inside individual domains; those must be inventoried by consumer use case before profile aggregation is designed.

## 4. Ownership decisions from observed code

| Concept | Current owner | Social Graph treatment |
|---|---|---|
| Canonical person/organization identity | Ecosystem Party | Reference PartyId |
| Authentication account/principal | Identity | Resolve through explicit Party identity association |
| Organization structure and tenant membership | Organization | Reference via explicit OrganizationPartyAssociation and public membership contract |
| Commercial/operational participant relationships | Ecosystem ParticipantRelationship | Keep separate; integrate by reference |
| Generic Party business relationship vocabulary | Ecosystem PartyRelationship | Do not overload for social connection/follow/block |
| Party-to-participant tenant business role | Ecosystem PartyParticipantAssociation | Reuse as business context, not social edge |
| Workforce worker engagement | Workforce | Keep worker lifecycle/qualification/contract there; resolve person identity explicitly |
| Customer lifecycle and sales relationships | Customer/CRM | Keep customer role, lead and pipeline ownership there |
| Driver, truck and carrier operational state | Mobility/Logistics/Fleet owning capabilities | Graph may reference discoverable subjects/resources; do not own operational state |
| Profile preference and privacy/consent | Not conclusively assigned by this audit | Must resolve owner before M3; no duplicate consent store |

## 5. Persistence, events, tenancy and reliability observations

### PostgreSQL
- Ecosystem Party has a PostgreSQL JDBC adapter and migrations.
- Party identity is global, while identity associations and PartyParticipantAssociation are tenant-scoped.
- Organization uses a separate top-level capability and explicit OrganizationPartyAssociation adapter.
- These distinctions must be preserved in Social Graph storage and queries.

### Outbox and reliability
- The repository has reusable reliability/outbox infrastructure under `core/framework/reliability`, including API, SPI, application and adapter modules.
- Multiple capabilities also implement mature local outbox patterns (for example construction, payment, courier, workspace, POS, document and affiliate).
- The inspected public contracts include `reliability-api` `OutboxEntry` and `reliability-spi` `OutboxStorePort`/`OutboxRelayPort`. The entry carries an event envelope, retry timing and attempt count; the store port exposes append, leased due-claim, conditional update, lookup and pending count.
- The contract is promising, but source inspection alone does not prove that the Social Graph aggregate write and outbox append can participate in one PostgreSQL transaction through the intended composition. M1 must inspect the adapter/unit-of-work transaction boundary and add an integration test proving atomic commit/rollback. Do not assume a separate `append` call is automatically atomic with the graph write.
- Social relationship writes and their durable event intent must be atomic. Projection delivery is asynchronous and idempotent if/when Neo4j is introduced.

### Tenant and security
- Foundation adapter `TenantContext` provides request-scoped tenant state with a thread-local fallback for non-request execution. Its own documentation says request tenant rebinding to a different value throws after security binding. This is an adapter utility, not a complete Social Graph actor/authorization contract; M1 must obtain trusted tenant and actor identity through the actual authenticated application boundary rather than reading an arbitrary payload/header.
- Canonical Party is global; Organization, memberships and several Party associations are tenant-scoped.
- Social Graph must distinguish global identity from tenant-owned relationship data and tenant-authorized visibility.
- Never authorize graph reads because a caller knows PartyId or an edge ID.
- Blocking, privacy, consent revocation and tenant scope must be enforced at the authoritative application boundary; a future Neo4j projection cannot be the only security gate.

### Neo4j
- No Social Graph Neo4j adapter or graph projection module was identified in the audited tree.
- No evidence in this source audit justifies making Neo4j mandatory.
- M1 should use PostgreSQL only. Neo4j adoption remains gated by representative workload benchmarks and a proven projection/reconciliation design.

## 5A. Additional M0 pass — profile, consent, authorization and transaction boundaries

This pass inspected the canonical `PersonRef` source, shared unit-of-work/outbox adapters, and the existing Security/Identity authorization contracts.

### Consumer inventory status and reactor boundaries

The identity mapping decision already records a concrete current consumer: Workforce's `Worker` aggregate stores `PersonRef`, while Ecosystem Party is the intended canonical cross-domain identity. The source file confirms `PersonRef` is a distinct `DomainId<UUID>` record, not a type alias for `PartyId`.

A complete repository-wide usage inventory could not be responsibly asserted from the available GitHub code-search results: searches for `PersonRef` and `PartyId` returned no indexed matches, while the recursive repository tree provides paths but not source references. The mapping decision document itself explicitly says all call sites, persisted columns, event payloads, APIs and serialized documents must be inventoried before migration. Keep this item open until a checked-out repository-wide source/data scan can be run (for example `git grep` plus migration/schema inspection); do not infer absence from an empty search index.

The build topology also matters for baseline evidence:

- `core/framework/pom.xml` is the shared `numou-framework` reactor (Java 21) and aggregates foundation, ecosystem, reliability, aggregate and other platform modules.
- `identity/pom.xml`, `organization/pom.xml`, `workforce/pom.xml` and `security/pom.xml` declare that shared framework parent and are relevant standalone capability aggregators.
- `core/pom.xml` is a separate legacy `numou-platform` reactor with Java 17 settings and a `Framework` module. It is not interchangeable with `core/framework/pom.xml`; do not assume a single root Maven invocation covers the entire repository.
- A recursive tree scan found 444 `pom.xml` files, confirming a multi-reactor repository rather than one root Maven project.

Recommended baseline sequence when a repository checkout/CI runner is available:
1. `mvn -f core/framework/pom.xml -DskipTests=false test` (platform shared framework; first validate the reactor can resolve its own modules).
2. `mvn -f identity/pom.xml test`.
3. `mvn -f organization/pom.xml test`.
4. `mvn -f workforce/pom.xml test`.
5. `mvn -f security/pom.xml test`.
6. Add Ecosystem and Reliability reactor tests through their actual module paths under `core/framework` (they are included in the framework reactor; target the module-specific poms if focused diagnosis is needed).

These are proposed commands, not executed test results. No build runner or repository checkout was available in this audit pass, so the baseline remains unrun.

### PersonRef and PartyId

- `PersonRef` is a UUID-backed universal person reference in `core/framework/foundation`. It is a distinct Java type from Ecosystem `PartyId`.
- The existing mapping decision document explicitly rejects implicit UUID aliasing and requires explicit validated mapping.
- The inspected `PartyIdentityAssociationRepository` resolves a tenant-scoped authenticated identity subject to Party; it is not a universal PersonRef-to-Party mapping.
- Therefore Social Graph must not cast `PersonRef.value()` to PartyId, even though both are UUID-backed. A dedicated, documented resolution port or verified existing mapping must be selected for PersonRef-bearing domain subjects.
- A complete consumer/persisted-column inventory is still outstanding; the recursive tree confirms the reference type exists but does not by itself enumerate every runtime producer or external integration.

### Profile and consent/privacy ownership

- The repository contains profile-shaped models owned by individual domains (for example construction project profile, madrasah profile, queue profile and automotive part profile). These are domain-specific records, not a canonical social/person profile service.
- The inspected shared foundation `configuration/profile` package is deployment/configuration profile resolution, not a person profile.
- The source-tree scan did not identify a single reusable platform consent/preference service that owns social visibility, discoverability, connection requests, blocking or consent revocation.
- A Syirkah `LifecycleConsent` record exists, but it records a partner's approval of a proposed Syirkah lifecycle change and requires evidence; it is not a general privacy-consent contract.
- Privacy ownership is therefore **not yet confirmed**. M1 must not treat a local consent value or profile visibility enum as a substitute for an authoritative consent/privacy policy. The social capability should own social relationship visibility/blocking rules while integrating with a broader consent service only if an established public contract is verified.

### Trusted actor, tenant and authorization boundary

- `security-application` exposes a verified `SecurityContext` containing a principal, tenant UUID and roles; its documentation says it is created after token validation and tenant resolution, not from raw client headers.
- `SecurityContextProvider` exposes `current()` and `require()` for application-layer access.
- `security-spi` exposes an authorization port, and Identity has a deny-by-default trusted authorization resolver that requires verified tenant scope and denies when evidence is missing or the policy resolver fails.
- Ecosystem's `PartyIdentityAssociationRepository` is the explicit bridge from authenticated identity subject to Party within a tenant.
- M1 should compose these contracts: obtain verified principal+tenant from the security context, authorize the requested social action through the existing authorization boundary, then resolve the actor Party through the tenant-scoped Party identity association. It must not accept actor PartyId, tenantId or roles as trusted command input.
- The exact integration wiring and permissions/resources for Social Graph still need to be specified and tested; the existence of these contracts is not proof they are currently wired into a Social Graph runtime.

### Unit-of-work and transactional outbox evidence

- Shared `PgUnitOfWork` wraps work in `Panache.withTransaction(work)` and documents aggregate and outbox rows committing together.
- `PgOutboxStore.append` obtains the active Panache session through `Panache.getSession()` and writes outbox rows through that session. This is a plausible same-transaction composition when invoked inside the same Panache unit of work.
- `OutboxDomainEventSink` transforms domain events into outbox entries and delegates append to the store.
- However, the existing `OutboxDomainEventSinkTest` uses an in-memory fake store. It verifies envelope metadata and serialization, not PostgreSQL transaction enlistment or rollback.
- Conclusion: the implementation path is identified, but atomicity is **not yet integration-test proven**. M1 must add a PostgreSQL test that writes a graph aggregate and appends its event in one `PgUnitOfWork`, then separately proves both commit together and both roll back when the use case fails. Also verify the actual CDI wiring selects this UoW and that the outbox adapter shares the same reactive session.

## 6. M0 gaps and decisions still required

The following are not yet proven by the source-tree audit and must remain open:

1. Full consumer inventory for PersonRef and PartyId across every domain, API, event, database column and external integration (Workforce Worker is confirmed; repository-wide scan remains blocked by unavailable checkout/code-search indexing).
2. Exact Party relationship API/persistence lifecycle expectations and whether any non-Java adapter exists outside the audited paths.
3. Canonical profile ownership and the existing user/person/organization profile implementations by consumer.
4. Authoritative consent/privacy preference owner and revocation contract.
5. Trusted actor/tenant context integration contract for Social Graph application commands.
6. Cross-tenant relationship policy for global Party identities and tenant-specific network edges.
7. Exact reusable outbox/inbox contract, transaction composition and migration conventions for a new capability.
8. Actual Maven build/test execution and baseline results for the shared framework plus Identity, Organization, Workforce and Security capability aggregators; commands and reactor boundaries are now recorded above.
9. Existing data volumes, relationship cardinality, expected traversal depths, latency objectives and benchmark workload. No Neo4j decision can be made from architecture preference alone.

## 7. M1 implementation recommendation

M1 should begin only after the open contract questions affecting its scope are resolved.

Recommended first implementation slice:

- Dedicated Social Graph capability using repository-consistent module naming and parent-POM conventions.
- Social subject reference based on canonical PartyId, with explicit resolution for authenticated actors and domain-owned resource subjects.
- Separate social relationship lifecycle; do not reuse PartyRelationshipType or ParticipantRelationship.
- Start with connection request/accept/reject/cancel/remove and follow/unfollow only if M1 scope can include the required authorization and uniqueness invariants.
- Block semantics must be included before exposing connection discovery or traversal; a block cannot be a later cosmetic feature.
- PostgreSQL authoritative persistence with tenant/visibility policy, idempotency, audit and transactional outbox.
- No Neo4j dependency, graph projection worker, feed, community, profile aggregate or recommendations in M1.
- Domain tests, application policy tests, PostgreSQL integration tests, tenant-isolation tests, idempotency/concurrency tests and architecture boundary tests.

## 8. Verification status

| Check | Status |
|---|---|
| Read repository `AGENTS.md` | Complete |
| Read ecosystem guardrails and ADR-003 | Complete |
| Inspect root tree and reactor roots | Complete |
| Inspect Party and Participant relationship models | Complete |
| Inspect Party identity persistence and migrations | Complete |
| Inspect Organization/Party association and membership | Complete |
| Inspect PersonRef/PartyId mapping decision | Complete |
| Identify dedicated Social Graph reactor on audited `main` | No dedicated reactor found |
| Run Maven builds/tests | Not run in this audit session |
| Run database integration tests | Not run |
| Inspect production deployment/data volumes | Not available in source-tree audit |
| Open PR | Not opened; explicit PR approval is required by repository guidance |

## 9. M0 exit criteria

M0 can be closed when:
- [x] Existing identity and relationship source models are inventoried.
- [x] Distinct relationship semantics and duplicate-ownership risks are documented.
- [x] Existing canonical Party persistence and explicit identity association are identified.
- [x] OrganizationId-to-PartyId mapping is shown to be explicit and auditable.
- [x] The PersonRef-to-PartyId compatibility gate is documented.
- [x] Repository Maven reactor topology and contributor rules are recorded.
- [ ] Full PersonRef/PartyId consumer and persisted-data inventory is completed.
- [ ] Profile and consent/privacy ownership is confirmed; source scan found no canonical social privacy/consent owner.
- [x] Existing verified SecurityContext, deny-by-default authorization and Party identity-association contracts are identified; Social Graph wiring/permissions still need agreement.
- [x] Public reliability outbox entry/store/relay contracts and Panache UoW/store composition are inspected; atomic transaction composition remains unproven and is an M1 integration-test gate.
- [ ] Baseline builds/tests are run on the affected reactors and recorded.
- [ ] M1 API, event, persistence and security contracts are finalized.

Until the remaining items are resolved, treat this report as a source-grounded partial M0 audit, not a declaration that M0 is fully closed.

## 10. Follow-up verification — canonical Party persistence boundary

A follow-up inspection of the exact source files resolves an ambiguity in the earlier summary and in older identity-convergence documents.

### Verified implementation

- `Party` is a real, minimal domain aggregate in `core/framework/ecosystem/ecosystem-domain/.../domain/model/Party.java`. It owns only immutable `PartyKind` (`PERSON` or `ORGANIZATION`) and ACTIVE/INACTIVE lifecycle. It does not own profile fields, names, contact points, organization structure, person attributes, or tenant membership.
- `PartyRepository` is a public application port with `save`, `findById`, and `findParty` lookup.
- `JdbcPartyRepository` is a production JDBC adapter. It persists to `ecosystem_party`, uses optimistic version checks for lifecycle updates, and performs its own JDBC connection/transaction for each save.
- `V2__canonical_parties.sql` creates the global UUID-keyed `ecosystem_party` table, constrained to PERSON/ORGANIZATION and ACTIVE/INACTIVE. Tenant membership and authentication links are explicitly separate.
- `PartyIdentityAssociationRepository` / its JDBC adapter and `V1__party_identity_associations.sql` provide tenant-scoped external identity associations. The association migration does not make a Party ID equivalent to a principal/UserId.

### Important limits for Social Graph

1. **Canonical identity persistence exists.** The earlier wording that there was no Party persistence adapter is stale or too broad and must not be repeated as a current source-tree finding.
2. **The Party aggregate is intentionally minimal.** Its existence does not establish a universal person profile, organization profile, consent service, or complete identity-resolution/migration system.
3. **The Party JDBC adapter owns its own transaction.** It obtains a `DataSource` connection, disables auto-commit, commits/rolls back locally, and does not use the inspected Panache `PgUnitOfWork` session. Therefore, the audit has not established that a Party save, future Social Graph write, and the shared outbox can be enlisted in one transaction. M1 must use a transaction composition supported by the selected reactor or introduce a narrowly scoped persistence/outbox unit-of-work contract; it must not assume the existing Panache UoW automatically covers this JDBC adapter.
4. **Party lifecycle events are not proven durable here.** `Party.register` raises `PartyRegistered`, but the inspected repository implementation does not persist/publish aggregate events. This audit does not claim Party registration is atomically coupled to the outbox.
5. **Identity convergence remains gated.** PersonRef/PartyId and OrganizationId/PartyId consumer and stored-data inventories remain incomplete. A canonical Party table does not prove existing Workforce or Organization records already map to it.

### M1 consequences

- Social Graph can reference `PartyId` for canonical person/organization subjects when the caller has validated the reference through the public contract.
- Do not add profile fields or duplicate identity state to Social Graph.
- Resolve authenticated actors through the tenant-scoped Party identity association and verified security context; do not accept caller-supplied actor PartyId as authority.
- Before implementing transactional graph events, inspect and select the actual JDBC/reactive persistence transaction model used by the target reactor. Add an integration test proving graph state and outbox event commit and roll back together.
- Treat the cross-capability Party and Organization mapping work as separate explicit gates; do not perform automatic UUID backfills.

### Evidence inspected in this follow-up

- `core/framework/ecosystem/ecosystem-domain/src/main/java/tech/kayys/numou/ecosystem/domain/model/Party.java`
- `core/framework/ecosystem/ecosystem-application/src/main/java/tech/kayys/numou/ecosystem/application/port/PartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/java/tech/kayys/numou/ecosystem/persistence/jdbc/JdbcPartyRepository.java`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V2__canonical_parties.sql`
- `core/framework/ecosystem/ecosystem-persistence-jdbc/src/main/resources/db/migration/V1__party_identity_associations.sql`

This follow-up is a GitHub source inspection only. It does not establish a successful Maven build, database migration run, transaction integration test, or deployed runtime behavior.
