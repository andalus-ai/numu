# Numou Social Graph

M1 provides the first PostgreSQL-backed social relationship vertical slice.

## Current scope

- Canonical Ecosystem Party references (`PartyId`); no duplicate person or organization identity.
- Tenant-scoped bilateral connection lifecycle: request, accept, reject, cancel, remove.
- `EcosystemSocialGraphActorResolver` resolves a verified issuer/subject through Ecosystem's tenant-scoped `PartyIdentityAssociationRepository`, then confirms the canonical Party exists and is active through `PartyLookupPort`.
- `EcosystemSocialGraphPartyDirectoryAdapter` implements Social Graph's active-Party check through the same public Ecosystem lookup port.
- `SocialGraphCommandFacade` is the public command entry point. The underlying `SocialGraphService` is package-private so other modules cannot bypass the facade through the application API.
- `SocialGraphService` enforces `SocialGraphAuthorizationPort` before Party checks or persistence; identity association alone does not grant tenant membership or capability permission.
- Idempotency keys are tenant-scoped and protected by command fingerprints.
- Optimistic version checks and a PostgreSQL partial unique index prevent duplicate pending/active connections for an unordered Party pair within one tenant.
- Append-only, actor-attributed lifecycle audit history is enforced by a PostgreSQL mutation-rejection trigger.
- A transaction-bound JDBC unit of work commits connection state, idempotency, audit and durable outbox event intent together.
- PostgreSQL is authoritative. Neo4j is not a runtime dependency.
- The JDBC adapter includes the Quarkus Flyway runtime extension. Hosts control migration timing with `quarkus.flyway.migrate-at-start=true`; Social Graph does not force migrations on consumers.

## Host composition and security

1. The HTTP/messaging host must authenticate the request and obtain tenant, issuer and subject from its verified security context. These values must never be copied from untrusted command-body fields.
2. The host passes the trusted tenant and `IdentitySubjectRef` to `SocialGraphCommandFacade`.
3. The facade calls `EcosystemSocialGraphActorResolver`, which requires an active tenant-scoped Party identity association and an active canonical Party.
4. The facade invokes the package-private command service; the service calls the host-provided `SocialGraphAuthorizationPort` to enforce tenant membership and the requested capability permission.
5. Domain transitions independently enforce participant roles (recipient accepts/rejects, requester cancels, either participant removes an active connection).

The Ecosystem adapters do not validate JWT signatures, choose the active tenant, or establish membership. Those are host/security-provider responsibilities. The authorization port must fail closed when membership or permission cannot be established. A Party ID, even one correctly resolved from an authenticated subject, is not authorization.

Do not infer identity equivalence from UUID equality between `UserId`, `PersonRef`, `OrganizationId`, driver IDs or `PartyId`. Social Graph consumes only explicit Ecosystem associations and canonical Party references.

## Reliability and non-goals

- The outbox table is an atomic event-intent store; broker relay/CDC and operational DLQ controls are not part of M1.
- The JDBC adapter exposes Mutiny `Uni` and schedules blocking database work on the default worker pool.
- No REST API, feed, follow/block, profile, unrestricted graph traversal, Neo4j projection or cross-tenant federation is included in M1.
- Consent, visibility and data-subject erasure policy must be defined before public discovery or social read APIs are exposed.



## Authenticated command gateway

The `security/social-graph-adapter` module exposes
`AuthenticatedSocialGraphCommandGateway`. It derives the tenant from the
current verified Numou `SecurityContext`, verifies that the principal is
authenticated and a member of that tenant, and obtains the issuer/subject
through `VerifiedIdentitySubjectProvider`. Callers supply only business
command data (recipient/connection ID and idempotency key); tenant, issuer,
and subject are not command arguments.

The host application must provide exactly one CDI implementation of
`VerifiedIdentitySubjectProvider` backed by its validated authentication
mechanism. It must return the original issuer and subject from verified
claims, not request headers, query parameters, or body fields. The current
SecurityContext contract does not itself expose issuer/subject claims, so the
adapter intentionally does not infer them from `PrincipalId` or
`Principal.handle`. If no provider or no active tenant-scoped Party identity
association exists, commands fail closed.

The adapter's CDI producer composes the Social Graph application service,
PostgreSQL unit of work, Ecosystem Party/identity adapter, and action-scoped
authorization adapter. The host still owns datasource configuration,
Flyway migration policy, the verified-claims provider, and the membership
snapshot freshness/revocation window.

## Security runtime composition

The optional `security/social-graph-adapter` module provides
`SecurityContextSocialGraphAuthorizationAdapter`, which implements the
application authorization port using the current verified `SecurityContext`.
It fails closed unless the context is authenticated, its tenant exactly
matches the resolved actor tenant, the principal is a member of that tenant,
and the action permission is present:

- `REQUEST_CONNECTION` requires `social-graph.create`.
- `MANAGE_CONNECTION` requires `social-graph.perform`.

The adapter intentionally does not map `PrincipalId` to `PartyId`. The
host must obtain the issuer/subject from verified authentication claims and
resolve the tenant-scoped canonical Party through the Social Graph actor
resolver before invoking `SocialGraphCommandFacade`. Never accept tenant,
issuer, subject, or actor Party identity from an untrusted command body.

The adapter consumes the current request's `SecurityContext` membership
snapshot. The host's security-context construction and token/session
invalidation policy must keep that snapshot within the documented membership
revocation window; this adapter does not independently query a second
membership store.

Because the security and framework modules are separate Maven aggregators,
install the framework artifacts before building the adapter:

```bash
mvn -f core/framework/pom.xml -pl social-graph,cache -am install
mvn -f security/pom.xml -pl social-graph-adapter -am verify
```

## Build and test

There is no repository-root Maven reactor. From the repository root:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

The dedicated Social Graph workflow runs this command on relevant pull requests and main-branch changes. Source changes are not considered verified until Maven and PostgreSQL/Testcontainers checks pass.

For a Quarkus host that includes this adapter, Flyway's default `classpath:db/migration` scan discovers the Social Graph migration from the dependency JAR. The host owns migration timing and should set `quarkus.flyway.migrate-at-start=true` only when ready to apply the schema. Do not configure a Social Graph-only location that hides other modules' migrations.


## Authenticated runtime command boundary

The optional `security/social-graph-adapter` module composes the application
service with the PostgreSQL unit of work, Ecosystem Party/identity adapter,
and action-scoped Numou authorization adapter. It exposes
`AuthenticatedSocialGraphCommandGateway`, whose command methods accept only
business inputs. Tenant scope is read from the current verified
`SecurityContext`; the issuer/subject is read from
`VerifiedIdentitySubjectProvider`.

The adapter includes `OidcVerifiedIdentitySubjectProvider`, which reads the
issuer and subject from the validated MicroProfile JWT supplied by Quarkus
OIDC. It does not infer either value from `PrincipalId`, `Principal.handle`,
request headers, query parameters, or body fields. The
`CurrentRequestSecurityContextProvider` bridges the request-scoped
`CurrentSecurity` populated by Numou's `RequestSecurityFilter` into the
canonical security-domain context and derives effective permissions from the
configured role matrix.

The default role matrix grants `social-graph.create` and
`social-graph.perform` to the `social-graph-member` role. Hosts should
assign that role only to users permitted to create and manage their own
connections, or replace/extend the matrix with their own policy. The route
must be covered by the security filter's protected prefixes and the JWT must
contain the configured tenant membership claims. Missing request context,
tenant membership, issuer/subject, permission, or active tenant-scoped Party
association fails closed.

The host still owns datasource configuration, Flyway migration policy, and
the freshness/revocation window for the request's membership snapshot.

Verification from the repository root:

```bash
mvn -f core/framework/pom.xml -pl :numou-social-graph-persistence-jdbc,:numou-ecosystem-persistence-jdbc,:numou-foundation-adapter,:numou-cache-adapter,:numou-observability-application -am install
mvn -f security/pom.xml -pl social-graph-adapter -am verify
```

