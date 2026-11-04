# Numou Social Graph

M1 introduces the first PostgreSQL-backed social relationship vertical slice.

## Current scope

- Canonical Ecosystem Party references (PartyId); no duplicate person or organization identity.
- Tenant-scoped bilateral connection request lifecycle: request, accept, reject, cancel, remove.
- Trusted command entry through `SocialGraphCommandFacade`: authenticated issuer/subject is resolved against the trusted tenant before the command service is called.
- Capability authorization is mandatory in `SocialGraphService` through `SocialGraphAuthorizationPort`; identity association alone does not grant access.
- Idempotency keys scoped by tenant, with command fingerprint protection.
- Optimistic version checks and a unique active/pending unordered Party-pair constraint.
- Append-only, actor-attributed lifecycle audit history enforced by a PostgreSQL mutation-rejection trigger.
- Explicit actor resolver and active Party directory adapter over Ecosystem public application ports.
- Transaction-bound JDBC unit of work: connection state, idempotency record, append-only audit record, and durable outbox event intent commit or roll back together.
- PostgreSQL only. Neo4j is not a runtime dependency.
- The JDBC adapter includes the Quarkus Flyway runtime extension, making its packaged `db/migration` resource discoverable to composed Quarkus hosts. Hosts explicitly control migration timing with `quarkus.flyway.migrate-at-start=true`; the capability does not force migration for deployments that do not opt in.

## Security and composition boundary

- Host adapters must obtain tenant and issuer/subject from a verified authentication/security context. Never populate them from untrusted request-body fields.
- `SocialGraphCommandFacade` resolves the authenticated issuer/subject to a tenant-associated canonical Party and rejects missing or cross-tenant resolver results.
- `SocialGraphService` performs capability authorization before Party validation or persistence side effects. Domain transitions independently enforce participant roles.
- Expose the facade—not direct construction of `SocialGraphActor` or direct invocation of `SocialGraphService`—to HTTP, messaging, and other untrusted input adapters.
- PartyDirectoryPort must be implemented by the composition layer using the public Ecosystem Party lookup contract. It must not infer Party identity from UserId, PersonRef, OrganizationId, or equal UUID values.
- The JDBC adapter exposes a Mutiny Uni and schedules blocking database work on the default worker pool.
- The outbox table is an atomic event-intent store. Broker relay/CDC integration and operational DLQ controls are not part of this M1 slice.
- No REST API, feed, follow/block, profile, Neo4j, or cross-tenant federation is included in M1.

## Build and test

There is no repository-root Maven reactor. From the repository root:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl social-graph -am verify
```

The build has not been claimed as passing until it is executed in CI or a checked-out workspace.

For a Quarkus host that includes this adapter, Flyway's default `classpath:db/migration` scan discovers the Social Graph migration from the dependency JAR. The host owns migration timing and should set `quarkus.flyway.migrate-at-start=true` only when ready to apply the schema. Do not configure a Social Graph-only location that hides other modules' migrations.
