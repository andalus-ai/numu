# Numou Social Graph

M1 introduces the first PostgreSQL-backed social relationship vertical slice.

## Current scope

- Canonical Ecosystem Party references (PartyId); no duplicate person or organization identity.
- Tenant-scoped bilateral connection request lifecycle: request, accept, reject, cancel, remove.
- Trusted actor context resolved through Ecosystem's tenant-scoped `PartyIdentityAssociationRepository` and canonical `PartyLookupPort`; command authorization is mandatory through `SocialGraphAuthorizationPort`.
- Idempotency keys scoped by tenant, with command fingerprint protection.
- Optimistic version checks and a unique active/pending unordered Party-pair constraint.
- Append-only, actor-attributed lifecycle audit history enforced by a PostgreSQL mutation-rejection trigger.
- Explicit actor resolver and active Party directory adapter over Ecosystem public application ports.
- Transaction-bound JDBC unit of work: connection state, idempotency record, append-only audit record, and durable outbox event intent commit or roll back together.
- PostgreSQL only. Neo4j is not a runtime dependency.
- The JDBC adapter includes the Quarkus Flyway runtime extension, making its packaged `db/migration` resource discoverable to composed Quarkus hosts. Hosts explicitly control migration timing with `quarkus.flyway.migrate-at-start=true`; the capability does not force migration for deployments that do not opt in.

## Boundaries

- PartyDirectoryPort must be implemented by the composition layer using the public Ecosystem Party lookup contract. It must not infer Party identity from UserId, PersonRef, OrganizationId, or equal UUID values.
- SocialGraphActor must be constructed from the authenticated tenant/Party association; never populate it from untrusted request fields.
- The JDBC adapter exposes a Mutiny Uni and schedules blocking database work on the default worker pool.
- The outbox table is an atomic event-intent store. Broker relay/CDC integration and operational DLQ controls are not part of this M1 slice.
- No REST API, feed, follow/block, profile, Neo4j, or cross-tenant federation is included in M1.


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
mvn -f core/framework/pom.xml -pl social-graph -am test
```

The build has not been claimed as passing until it is executed in CI or a checked-out workspace.

For a Quarkus host that includes this adapter, Flyway's default `classpath:db/migration` scan discovers the Social Graph migration from the dependency JAR. The host owns migration timing and should set `quarkus.flyway.migrate-at-start=true` only when ready to apply the schema. Do not configure a Social Graph-only location that hides other modules' migrations.
