# Social Graph M1 — PostgreSQL Core Implementation

- **Status:** M1 merged; runtime Flyway discovery dependency added in follow-up hardening branch; build/CI verification pending
- **Date:** 2026-11-04
- **Branch:** `social-graph/m1-core-postgresql-mainline`
- **Architecture:** [Social Graph Foundation](SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md)
- **Audit:** [M0 Repository Audit](SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md)

## 1. Scope delivered

M1 implements the first durable social relationship vertical slice:

- Dedicated `core/framework/social-graph` Maven reactor included by `core/framework/pom.xml`.
- Separate domain, SPI, application, and JDBC persistence modules.
- Social connection lifecycle: request, accept, reject, cancel, remove.
- Canonical PartyId references from Ecosystem; no duplicate Person or Organization identity.
- Tenant-scoped relationship storage.
- Actor context contract that must be populated by the authenticated host boundary.
- Active Party validation and authenticated subject resolution through Ecosystem's public `PartyLookupPort` and `PartyIdentityAssociationRepository` contracts.
- Tenant-scoped idempotency key and actor-bound command fingerprint protection.
- Optimistic version checking.
- Unique PostgreSQL partial index prevents duplicate pending/active connections for an unordered Party pair in one tenant.
- One JDBC Unit of Work uses one physical connection for connection state, idempotency state, append-only actor-attributed audit, and outbox event intent.
- Durable local outbox rows with actor provenance, committed atomically with the relationship change.
- Append-only audit history enforced by a PostgreSQL trigger that rejects update/delete.
- Domain and application service tests for lifecycle, actor authorization, duplicate commands, and event intent.

## 2. Package and reactor layout

```text
core/framework/social-graph/
  pom.xml
  README.md
  social-graph-domain/
  social-graph-spi/
  social-graph-application/
  social-graph-persistence-jdbc/
```

The framework reactor includes `social-graph`; the framework dependency management declares the four public artifacts.

## 3. Domain and command behavior

A connection is bilateral but retains requester/recipient direction for the request lifecycle.

| Command | Authorized actor | Transition |
|---|---|---|
| Request | Requester | New → PENDING |
| Accept | Recipient | PENDING → ACTIVE |
| Reject | Recipient | PENDING → REJECTED |
| Cancel | Requester | PENDING → CANCELLED |
| Remove | Either participant | ACTIVE → REMOVED |

Self-connections are rejected. Transitions from terminal states are rejected. The recipient cannot cancel a request on behalf of the requester, and the requester cannot accept their own request.

The current persistence model is tenant-scoped. Cross-tenant connection discovery is not enabled. A global Party identity does not imply global visibility of its social relationships.

## 4. Persistence and consistency

Migration: `social-graph-persistence-jdbc/src/main/resources/db/migration/V1__social_graph_connections.sql`.

Tables:

- `sg_connection`: authoritative lifecycle state and optimistic version.
- `sg_command_idempotency`: tenant-scoped idempotency result key/fingerprint.
- `sg_outbox`: durable event intent, schema version, aggregate version and delivery metadata.

The partial unique index covers only `PENDING` and `ACTIVE` rows for a tenant and canonical unordered Party pair. Terminal historical records remain available without preventing a later new request. PostgreSQL documents partial unique indexes as a supported way to enforce uniqueness over a subset of rows: https://www.postgresql.org/docs/current/indexes-partial.html.

The JDBC adapter executes the command callback and all persistence/outbox writes using one connection and commits once. Callback failures and commit failures trigger rollback. The outbox is durable but this milestone does not yet include a relay, broker integration, lease worker, or DLQ operations. Those should integrate with the shared reliability capability after its transaction and serialization contracts are verified.

## 5. Security and integration boundaries

- `SocialGraphActor` is a trusted application context, not a DTO for untrusted request fields.
- The host must resolve authenticated principal → tenant-scoped Party association using the existing Identity/Ecosystem association contract.
- Do not equate UserId, PersonRef, OrganizationId and PartyId by UUID value.
- `PartyDirectoryPort` is intentionally a public application port. The composition root must implement it using the Ecosystem Party lookup contract and validate active status. It must not query Ecosystem persistence tables directly.
- Tenant authorization for an actor and relationship visibility must be enforced by the host/application boundary.
- The application contracts are reactive Mutiny ports; the JDBC adapter schedules blocking work on Mutiny's default worker pool.
- No Neo4j driver or Cypher dependency exists in M1.

## 6. Verification

Tests added:

- `SocialConnectionLifecycleTest`
- `SocialGraphServiceTest`

Expected build command from repository root:

```bash
mvn -f core/framework/pom.xml -pl social-graph -am test
```

**Verification status:** Not run in this session. Do not mark M1 complete or open a review-ready PR until the command passes in a checked-out workspace/CI, PostgreSQL migration and transaction integration tests pass, and the composition layer's PartyDirectory/actor binding is implemented and tested.

## 7. Remaining M1 hardening before review

1. Run the exact Maven reactor build and resolve any dependency or compilation failures.
2. Add Testcontainers PostgreSQL tests proving migration validity, uniqueness under concurrent requests, tenant isolation, optimistic conflict behavior, and rollback of state + idempotency + outbox on injected failure.
3. Add an application composition adapter for authenticated actor resolution and Party lookup; no unsafe UUID conversions.\n4. Verify append-only audit trigger behavior and PostgreSQL transaction rollback tests in CI.
5. Verify Flyway discovery/registration in an actual Quarkus host runtime; the adapter now supplies the Quarkus Flyway extension, while each host controls `quarkus.flyway.migrate-at-start`.
6. Add read/list contracts with bounded pagination only after privacy and visibility rules are agreed; do not expose unrestricted traversal.
7. Verify outbox event naming/payload compatibility and define relay ownership with the shared reliability capability.
8. Update this document with exact test commands and CI evidence before requesting review.

## 8. Explicitly excluded

Follow/block/mute, profile aggregation, communities, content/feed, graph traversal, search, recommendations, Neo4j projection, REST endpoints, cross-tenant federation, and domain-specific driver/job/worker relationships are later milestones.
