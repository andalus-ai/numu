# Social Graph M1 — PostgreSQL Core Implementation

- **Status:** M1 trusted command facade and concrete Ecosystem identity/Party adapters are implemented on `feat/social-graph-m1-ecosystem-identity-adapters`; build, PostgreSQL CI and host-runtime authentication wiring remain pending
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
- `SocialGraphCommandFacade` as the only public application command entry point; `SocialGraphService` is package-private to prevent cross-package bypass.
- `EcosystemSocialGraphActorResolver` resolves a verified issuer/subject through the tenant-scoped `PartyIdentityAssociationRepository`, verifies association tenant/subject/status, and confirms the canonical Party is active through `PartyLookupPort`.
- `EcosystemSocialGraphPartyDirectoryAdapter` adapts canonical Party lifecycle checks to `PartyDirectoryPort`.
- Tenant-scoped idempotency key and actor-bound command fingerprint protection.
- Optimistic version checking.
- Unique PostgreSQL partial index prevents duplicate pending/active connections for an unordered Party pair in one tenant.
- One JDBC Unit of Work uses one physical connection for connection state, idempotency state, append-only actor-attributed audit, and outbox event intent.
- Durable local outbox rows with actor provenance, committed atomically with the relationship change.
- Append-only audit history enforced by a PostgreSQL trigger that rejects update/delete.
- Domain and application tests for lifecycle, authorization, duplicate commands and event intent; adapter tests cover active/missing identity associations, mismatched tenant/subject, inactive Parties and active-Party lookup.

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

- `SocialGraphCommandFacade` is the public application command boundary. `SocialGraphService` is package-private so other modules cannot directly construct or invoke the command service through the application artifact.
- The host must authenticate the request and obtain tenant + issuer/subject from verified security context. Never accept these values from untrusted request-body fields.
- `EcosystemSocialGraphActorResolver` uses `PartyIdentityAssociationRepository.findActiveByTenantAndIdentity(tenant, issuerSubject)` and `PartyLookupPort.findParty(partyId)`. It rejects absent, mismatched, revoked or inactive associations/Parties and returns the canonical PartyId only.
- `EcosystemSocialGraphPartyDirectoryAdapter` implements active Party validation through the same public Party lookup contract. No direct Ecosystem persistence-table access is permitted.
- The facade resolves the actor before delegating. `SocialGraphService` invokes `SocialGraphAuthorizationPort` before Party checks or persistence. The host-provided authorization implementation must validate tenant membership and the command permission and fail closed. Identity association alone is not membership or authorization.
- Domain transitions independently enforce participant roles: recipient accepts/rejects, requester cancels, either participant removes an active connection.
- The adapters do not validate JWT signatures, select the active tenant, or establish tenant membership. Those remain host/security-provider responsibilities; end-to-end host wiring is still pending.
- Do not equate UserId, PersonRef, OrganizationId, driver IDs and PartyId by UUID value.
- The application contracts are reactive Mutiny ports; the JDBC adapter schedules blocking work on Mutiny's default worker pool.
- No Neo4j driver or Cypher dependency exists in M1.

The facade is a composition boundary, not a replacement for the host's authentication mechanism. The tenant and issuer/subject arguments are trusted only when populated by the host from verified security context.

## 6. Verification

Tests present in the source tree include:

- `SocialConnectionLifecycleTest`
- `SocialGraphServiceTest`
- `SocialGraphCommandFacadeTest` (all five commands resolve identity and require capability authorization; missing and cross-tenant identities fail before persistence)
- `EcosystemSocialGraphIdentityAdapterTest` (identity association, tenant/subject matching, active Party resolution and Party directory lifecycle)
- `JdbcSocialGraphUnitOfWorkPostgresTest` (Testcontainers; includes migration, commit/rollback, uniqueness, optimistic concurrency, audit immutability, tenant-scoped lookup and UUID ordering checks)

Expected CI command:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

**Verification status:** Not run in this session. The SQL dollar-quote delimiter and Mutiny-await fixes were confirmed present on the reviewed current `main` before this branch was created. The new Ecosystem adapters and facade tests are source changes only; no Maven compilation, PostgreSQL/Testcontainers execution or CI pass is claimed. Do not mark M1 complete until the workflow passes and a real host wires verified authentication context, the resolver, Party directory and fail-closed authorization port.

## 7. Remaining M1 hardening before review

1. Run the exact Maven reactor build and resolve any dependency or compilation failures.
2. Verify Testcontainers PostgreSQL tests execute (not silently skip when Docker is unavailable), including migration validity, uniqueness, tenant isolation, optimistic conflict and rollback behavior.
3. Wire the facade into an actual HTTP/messaging host using verified authentication context; test the full principal → tenant + issuer/subject → Ecosystem Party association → active Party → authorization → command path.
4. Provide a concrete host `SocialGraphAuthorizationPort` implementation and verify membership and command permissions fail closed.
5. Verify append-only audit trigger behavior and transaction rollback tests in CI.
6. Verify Flyway discovery/registration in an actual Quarkus host runtime; each host controls `quarkus.flyway.migrate-at-start`.
7. Add read/list contracts only after privacy, consent and visibility rules are agreed; do not expose unrestricted traversal.
8. Verify outbox event naming/payload compatibility and define relay ownership with the shared reliability capability.
9. Update this document with exact test commands and CI evidence before requesting review.

## 8. Explicitly excluded

Follow/block/mute, profile aggregation, communities, content/feed, graph traversal, search, recommendations, Neo4j projection, REST endpoints, cross-tenant federation, and domain-specific driver/job/worker relationships are later milestones.
