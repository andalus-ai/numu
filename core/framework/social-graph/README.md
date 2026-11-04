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

## Build and test

There is no repository-root Maven reactor. From the repository root:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

The dedicated Social Graph workflow runs this command on relevant pull requests and main-branch changes. Source changes are not considered verified until Maven and PostgreSQL/Testcontainers checks pass.

For a Quarkus host, Flyway's default `classpath:db/migration` scan discovers the Social Graph migration from the adapter dependency JAR. The host owns migration timing; do not configure a Social Graph-only location that hides other modules' migrations.
