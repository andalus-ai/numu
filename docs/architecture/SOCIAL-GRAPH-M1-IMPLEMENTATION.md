# Social Graph M1 — PostgreSQL Core Implementation

- **Status:** M1 command security composition has a new trusted facade on `feat/social-graph-m1-trusted-command-facade`; build, PostgreSQL and host-runtime verification remain pending
- **Date:** 2026-11-04
- **Architecture:** [Social Graph Foundation](SOCIAL-GRAPH-FOUNDATION-ARCHITECTURE.md)
- **Audit:** [M0 Repository Audit](SOCIAL-GRAPH-M0-REPOSITORY-AUDIT.md)

## 1. Scope delivered

M1 implements the first durable social relationship vertical slice:

- Dedicated `core/framework/social-graph` Maven reactor included by `core/framework/pom.xml`.
- Separate domain, SPI, application, and JDBC persistence modules.
- Social connection lifecycle: request, accept, reject, cancel, remove.
- Canonical PartyId references from Ecosystem; no duplicate Person or Organization identity.
- Tenant-scoped relationship storage.
- Actor resolution contract based on authenticated issuer/subject plus trusted tenant context.
- `SocialGraphCommandFacade` as the trusted application entry point for all five commands.
- Capability authorization through `SocialGraphAuthorizationPort` inside `SocialGraphService`.
- Active Party validation, tenant-scoped idempotency key and actor-bound command fingerprint protection.
- Optimistic version checking.
- Unique PostgreSQL partial index prevents duplicate pending/active connections for an unordered Party pair in one tenant.
- One JDBC Unit of Work uses one physical connection for connection state, idempotency state, append-only actor-attributed audit, and outbox event intent.
- Durable local outbox rows with actor provenance, committed atomically with the relationship change.
- Append-only audit history enforced by a PostgreSQL trigger that rejects update/delete.

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

The partial unique index covers only `PENDING` and `ACTIVE` rows for a tenant and canonical unordered Party pair. Terminal historical records remain available without preventing a later new request.

The JDBC adapter executes the command callback and all persistence/outbox writes using one connection and commits once. Callback failures and commit failures trigger rollback. The outbox is durable but this milestone does not yet include a relay, broker integration, lease worker, or DLQ operations.

## 5. Security and integration boundaries

- Host adapters must obtain tenant and issuer/subject from a verified authentication/security context, never from request-body fields.
- `SocialGraphCommandFacade` resolves the issuer/subject through `SocialGraphActorResolver` for the trusted tenant before invoking a command.
- The facade rejects missing identity associations and rejects a resolver result whose tenant differs from the trusted tenant.
- `SocialGraphService` invokes `SocialGraphAuthorizationPort` before active-Party checks or persistence side effects. Identity association alone does not grant capability access.
- Domain transitions independently enforce participant-specific permissions: recipient accepts/rejects, requester cancels, either participant removes an active connection.
- Expose the facade—not caller-constructed `SocialGraphActor` instances or direct service invocation—to HTTP, messaging, and other untrusted input adapters.
- `PartyDirectoryPort` is a public application port. The composition root must implement it using Ecosystem's public Party lookup contract and validate active status. It must not query Ecosystem persistence tables directly.
- Do not equate UserId, PersonRef, OrganizationId and PartyId by UUID value.
- No Neo4j driver or Cypher dependency exists in M1.

The facade is a composition boundary, not a replacement for the host's authentication mechanism. The tenant and issuer/subject arguments are trusted only when populated by the host from verified security context.

## 6. Verification

Tests present in the source tree include:

- `SocialConnectionLifecycleTest`
- `SocialGraphServiceTest`
- `SocialGraphCommandFacadeTest` — verifies unassociated subjects and cross-tenant resolver results are denied before persistence, and capability authorization is invoked before persistence.
- `JdbcSocialGraphUnitOfWorkPostgresTest` (Testcontainers; migration, commit/rollback, uniqueness, optimistic concurrency, audit immutability, tenant-scoped lookup and UUID ordering checks).

Expected CI command:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

**Verification status:** Not run in this session. The earlier current-main review identified an invalid PostgreSQL dollar-quote delimiter in the audit trigger function and a missing Mutiny await in the UUID-ordering integration test. Both repairs are now confirmed present on current `main`. The new facade and tests are source changes only; no compilation, PostgreSQL test, or CI pass is claimed.

## 7. Remaining M1 hardening before review

1. Run the exact Maven reactor verification and resolve compilation/dependency failures.
2. Confirm Testcontainers PostgreSQL tests execute rather than silently skip because Docker is unavailable.
3. Integrate the facade into the actual host composition using verified principal → trusted tenant + issuer/subject → tenant-scoped Party association → active Party → authorization → command.
4. Decide whether PostgreSQL RLS is required as defense-in-depth for graph, audit, idempotency and outbox tables under the actual application/worker roles.
5. Add concurrency coverage for duplicate idempotency keys and active-pair races, and confirm retry/replay behavior.
6. Define consent, visibility, block and deletion semantics before adding read/list/search or cross-tenant discovery.
7. Verify outbox event naming/payload compatibility and define relay ownership with the shared reliability capability.
8. Update this document with exact test and CI evidence before requesting review.

## 8. Explicitly excluded

Follow/block/mute, profile aggregation, communities, content/feed, graph traversal, search, recommendations, Neo4j projection, REST endpoints, cross-tenant federation, and domain-specific driver/job/worker relationships are later milestones.
