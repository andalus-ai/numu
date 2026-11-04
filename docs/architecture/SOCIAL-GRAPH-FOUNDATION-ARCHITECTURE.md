# Social Graph Foundation — Architecture and M0 Audit

- **Status:** Proposed architecture; M0 repository audit required before implementation
- **Date:** 2026-11-03
- **Repository:** `andalus-ai/numu`
- **M0 audit:** [Social Graph M0 Repository Audit](SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md) — source-grounded partial audit; build/runtime and remaining ownership checks are pending
- **Related:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Domain capability matrix](DOMAIN-CAPABILITY-MATRIX.md), [Organization/Party convergence](organization-party-identity-convergence.md), [Person/Party identity mapping](person-party-identity-mapping-decision.md), [Party/Contact/Address contract](contact-address-capability-contract.md), [Customer contract](customer-capability-contract.md)

## 1. Purpose

Establish a reusable, application-neutral Social Graph capability for professional networking, communities, recruitment, crowdsourcing, driver/truck networks, workforce sourcing, entrepreneur networks, and B2B partner discovery.

The Social Graph is shared relationship infrastructure. It is not a replacement for Identity, Party, Organization, Customer, CRM, Workforce, Geo, Scheduling, Mobility, Logistics, Jobs, Marketplace, or domain-owned reputation and eligibility policies.

This document is a target architecture and M0 audit plan. It does not claim that a Social Graph implementation, graph database adapter, or production integration already exists.

## 2. Core architectural decisions

### 2.1 One canonical identity; graph nodes are references

- Identity owns authentication principals and login lifecycle.
- Ecosystem Party is the intended canonical person/organization identity owner, subject to the existing Party persistence and identity-convergence gates.
- Organization owns organization structure and organizational lifecycle; it must not create a competing Party identity.
- Workforce, Customer, CRM, Driver, Carrier, Entrepreneur and other domains own their own role, engagement, and business lifecycle records.
- A graph node identifies a subject through a typed, stable external reference. It does not copy the source aggregate or become another person/organization master record.
- Do not assume UUID equality proves that PersonRef, PartyId, OrganizationId, UserId, WorkerId, or any domain identifier refers to the same entity. Use an explicit, validated mapping contract where needed.

### 2.2 Distinguish Party relationships from social relationships

The existing Ecosystem Party model includes generic Party relationship vocabulary. M0 must inspect its exact semantics and implementation before adding anything.

The Social Graph must not duplicate legal, ownership, household, corporate-control, or other canonical Party relationship facts already owned elsewhere. It owns social/network interaction semantics such as:

- connection request, acceptance, rejection, and removal;
- follow and unfollow;
- block and unblock;
- network membership and participation;
- professional endorsements, where consent and provenance are explicit;
- social interaction and discovery preferences.

If an existing Party relationship has the exact same meaning and invariants, reuse its public contract. If meanings differ, keep separate types and explicit references. Similar names are not proof of semantic equivalence.

### 2.3 PostgreSQL is authoritative; Neo4j is optional

PostgreSQL is the default and authoritative persistence for Social Graph commands and durable state:

- stable graph subject references and relationship records;
- relationship lifecycle, visibility, consent and policy state;
- tenant/actor context and audit history;
- idempotency records and transactional outbox;
- projection checkpoints and reconciliation metadata.

Neo4j is an optional, rebuildable graph projection/query engine. It may be enabled when measured workloads justify multi-hop traversal, path analysis, recommendation computation, or graph algorithms.

Neo4j must not be required to run the core capability, and it must not become a second authoritative write model. No business command may require a synchronous dual-write to PostgreSQL and Neo4j.

### 2.4 Projection and synchronization

The target flow is:

1. An authorized application command validates tenant, actor, visibility, relationship policy, and idempotency.
2. PostgreSQL commits the authoritative state and outbox event in one transaction.
3. An outbox relay publishes versioned events.
4. A projection worker applies events to the optional Neo4j projection.
5. Projection processing is idempotent and ordered per aggregate/subject where ordering is required.
6. Checkpoints, lag, retries, poison events, and dead-letter recovery are observable.
7. Reconciliation detects missing, stale, or inconsistent projected records; a projection can be rebuilt from authoritative state/events.

The graph projection is eventually consistent. APIs must declare whether they read authoritative PostgreSQL state or an eventually consistent graph query. Security-critical authorization, blocking, consent revocation, and tenant access decisions must not rely solely on a stale Neo4j projection. Apply authoritative policy checks before returning graph-derived results.

A first implementation may use a Numou-owned projection worker and adapter. Kafka/CDC connectors are optional infrastructure choices, not mandatory domain dependencies. Do not introduce Kafka or Neo4j solely to implement M1.

### 2.5 Stable identity and tenancy

- Never expose or persist Neo4j internal node/relationship IDs as Numou identifiers.
- Use stable Numou-owned IDs or validated typed external references.
- Define whether each node/edge is globally addressable, tenant-owned, tenant-visible, or intentionally cross-tenant.
- Tenant context must come from trusted authentication/application context, not an arbitrary payload field.
- Cross-tenant discovery is denied by default and requires an explicit product policy, consent, and authorization.
- Tenant isolation applies to reads, traversals, counts, search, recommendations, exports, caches, and projection jobs.
- A known node ID or graph path is not authorization.
- Block, privacy, and consent restrictions must be applied to traversal and result hydration, including multi-hop results.

### 2.6 Domain extensions own their semantics

The foundation may provide generic graph primitives and narrowly shared social relationship behavior. Extensions own business-specific facts and state:

| Extension | Owns | Social Graph contributes |
|---|---|---|
| Professional network | Experience, professional profile sections, career preferences | Connections, follows, professional discovery |
| Jobs/recruitment | Vacancies, applications, hiring stages, employer policies | Candidate/employer discovery and network referrals |
| Crowdsourcing | Opportunities, offers, selection, engagement, completion | Participant and provider discovery |
| Driver/truck network | Driver onboarding, vehicles, carrier authority, availability eligibility | Network links and discovery references |
| Workforce sourcing | Worker engagement, qualifications, contracts and employment | Skill/relationship discovery references |
| Entrepreneur network | Business profile, ventures, funding/partnership workflows | Founder, organization and partner connections |
| Communities | Community policy, moderation, membership lifecycle and content | Shared network identity and relationship primitives |
| B2B/supplier network | Supplier qualification, catalog, commercial terms and orders | Business discovery and partnership links |

Do not put vacancies, driver availability, truck capacity, employment contracts, orders, payments, or a universal reputation score into the Social Graph.

## 3. Target capability packaging

Follow the repository's actual Maven reactor conventions after M0 inventory. The following is a logical target, not an instruction to create every artifact or a new deployment:

```text
social-graph/
  social-graph-domain/
  social-graph-spi/
  social-graph-application/
  social-graph-adapter/              # composition/integration as justified
  social-graph-persistence-jdbc/     # PostgreSQL authoritative adapter
  social-graph-projection-spi/       # optional graph projection contract
  social-graph-neo4j-adapter/        # optional, isolated adapter
  social-graph-api/                  # only if an API reactor is justified
  social-graph-bom/                  # only if repository conventions require it
```

Do not create an independent microservice by default. The capability must be consumable in a standalone application, modular monolith, or distributed deployment using the same domain/application contracts.

Domain code must not depend on JDBC, Neo4j driver, Cypher, Kafka, HTTP, CDI/runtime, or vendor-specific libraries. Keep Neo4j dependencies isolated in its adapter.

## 4. Initial domain vocabulary (subject to M0 evidence)

Candidate concepts:

- `GraphSubjectRef`: typed reference to an existing Party or domain-owned subject; not a copied identity aggregate.
- `SocialConnection`: bilateral relationship with explicit request/acceptance lifecycle.
- `FollowRelationship`: directional subscription.
- `BlockRelationship`: privacy/safety restriction with enforcement semantics.
- `NetworkMembership`: participation in a named network/community where that lifecycle is owned by this capability.
- `RelationshipType`: controlled, versioned semantic identifier; avoid a universal enum containing industry-specific states.
- `VisibilityPolicyRef` / consent references: reference the authoritative policy/consent owner rather than duplicate its source of truth.
- `GraphProjectionPosition`: internal projection progress/version metadata, never a business identity.

Do not finalize aggregate boundaries, IDs, relationship cardinality, or table layout until M0 inspects existing Party relationships, ecosystem persistence, tenancy, event/outbox conventions, and all likely consumers.

## 5. M0 — Repository and architecture audit

### Work items

1. Inspect existing Social/Community/Network/Relationship/Connection/Profile/Follow/Block/Endorsement/Graph packages and APIs across all Maven reactor roots.
2. Inspect Ecosystem Party relationship types, repositories, persistence adapters, migrations, and application ports. Record which semantics are already owned and which are only vocabulary.
3. Inspect Identity, Organization, Party, Workforce, Customer, CRM, Geo, Scheduling, Mobility, Logistics, Marketplace and Jobs contracts that may supply graph subjects or consume graph discovery.
4. Inventory canonical identifiers and mappings. Explicitly document unresolved PersonRef/PartyId, OrganizationId/PartyId, UserId/PartyId, and WorkerId/PartyId relationships; do not infer equivalence from UUID shape.
5. Inspect tenancy and authorization contracts, including cross-tenant organizations/partners, public profile visibility, consent, blocking, deletion/deactivation, and data export requirements.
6. Inspect the repository's PostgreSQL migration, UnitOfWork, outbox/inbox, idempotency, event versioning, observability, and test conventions.
7. Inspect existing graph/Neo4j dependencies, if any, and determine whether there is a real workload or existing operational standard that warrants a Neo4j adapter.
8. Inspect all Maven reactor roots and their parent-POM conventions. Do not assume a repository-root Maven build exists.
9. Produce an evidence-based ownership/dependency map, proposed public contracts, threat/privacy model, persistence design, and M1 implementation slice.
10. Record test/build commands and baseline results for the exact affected reactors. Do not claim unrun tests or unavailable infrastructure as passing.

### M0 acceptance criteria

- [ ] Existing related modules and implementations are inventoried with paths and public contracts.
- [ ] Existing Party relationship semantics are compared against proposed social relationship semantics; duplicate ownership is prevented.
- [ ] Canonical identity and unresolved identifier mappings are documented without implicit casts.
- [ ] Tenant, cross-tenant, privacy, block, consent, and authorization boundaries are explicit.
- [ ] PostgreSQL authoritative state and outbox/projection consistency model are documented.
- [ ] Neo4j is optional and adapter-isolated; a measured use-case threshold for enabling it is documented.
- [ ] Actual Maven reactor roots, build conventions, migration conventions, and test commands are recorded.
- [ ] M1 scope, dependencies, API/event contracts, migration strategy, and verification plan are reviewable before implementation.

## 6. M1 — Social Graph core (initial vertical slice implemented)

The initial M1 implementation is recorded in [Social Graph M1 Implementation](SOCIAL-GRAPH-M1-IMPLEMENTATION.md).

Implemented on branch `social-graph/m1-core-postgresql-mainline`:

- Dedicated framework Maven reactor: `core/framework/social-graph`.
- Domain, SPI, application, and JDBC persistence modules.
- Tenant-scoped connection request/accept/reject/cancel/remove lifecycle over canonical Ecosystem `PartyId`.
- Trusted actor-context contract and Party-active validation port.
- Tenant-scoped idempotency with command fingerprint protection.
- Optimistic version checks and database uniqueness for open unordered Party pairs.
- PostgreSQL JDBC Unit of Work that writes connection state, idempotency, and durable outbox event intent on one physical transaction.
- Domain, application and PostgreSQL Testcontainers tests.

M1 is **not yet review-ready**. Maven/CI has not been run in this session. The remaining gates are exact reactor compilation, PostgreSQL integration execution, host actor/Party-directory composition, runtime migration discovery, and confirmation of shared outbox relay ownership. Follow/block, feeds, profiles, traversal, search, recommendations, Neo4j and REST endpoints remain out of scope.

### M1 acceptance criteria

- [x] Initial connection lifecycle domain and application contracts.
- [x] PostgreSQL schema for tenant-scoped connection state, idempotency and outbox.
- [x] Single-connection JDBC unit of work for atomic state/idempotency/outbox commit and rollback.
- [x] Unit and integration test sources added.
- [ ] Maven reactor build and tests executed successfully.
- [ ] PostgreSQL Testcontainers tests executed successfully in CI.
- [ ] Host actor and Party directory composition implemented and verified.
- [ ] Runtime migration discovery and outbox relay integration verified.

## 7. Neo4j enablement gate

Enable the Neo4j adapter only when all of the following are true:

1. A concrete product workload needs multi-hop traversal/path analysis/recommendation beyond acceptable PostgreSQL query performance.
2. Representative graph size, relationship density, traversal depth, latency, and concurrency are measured.
3. PostgreSQL recursive queries/indexes/read models have been benchmarked as the baseline.
4. The projection's eventual-consistency and stale-data behavior are acceptable for the specific use case.
5. Tenant and privacy filters can be enforced without leaking nodes/edges through paths, counts, or cached results.
6. Replay, idempotency, backfill, checkpoint recovery, reconciliation, deletion propagation, and disaster recovery are tested.
7. Operational cost, licensing/edition features, backup/restore, monitoring, and deployment burden are accepted.

Until then, PostgreSQL-only deployment is the supported default. The domain and application contracts must nevertheless leave room for a future graph query/projection adapter.

## 8. Explicit non-goals

- Replacing Ecosystem Party or Identity.
- Creating another Person, Organization, Customer, Worker, Driver, or Vehicle master record.
- Making Neo4j mandatory or using two writable sources of truth.
- Exposing arbitrary Cypher or unrestricted graph traversal to API consumers.
- Treating graph connections as authorization grants or proof of trust.
- One universal reputation score across unrelated domains.
- Splitting the capability into a microservice without an operational reason.
- Claiming implementation completeness based on this architecture document.
