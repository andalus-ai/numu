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


## M2 relationship lifecycle foundation

M2 introduces a separate directed-edge model for `FOLLOW`, `BLOCK`, and `MUTE`. It does not overload the bilateral `SocialConnection` approval state machine. The initial domain contract provides:

- Stable built-in relationship types; tenant-defined type registration is not yet part of the contract.
- Canonical source/target Party references and tenant ownership.
- Explicit creation provenance and bounded immutable metadata.
- Optional validity windows using inclusive `validFrom` and exclusive `validUntil` semantics.
- Versioned ACTIVE/REMOVED lifecycle with source-Party-owned removal.
- No persistence, command gateway, public discovery, feed, or graph traversal is exposed by this domain-only increment. Those require subsequent application/persistence work and the agreed privacy/consent policy.

A BLOCK edge is stored with its actor as source. Any future interaction/discovery policy must evaluate a block in either direction; callers must not infer that a directed storage representation makes block enforcement directional.


## M2d authoritative relationship policy

The application exposes `SocialGraphPolicyService` backed by
`SocialGraphRelationshipPolicyPort`. The JDBC adapter evaluates policy against
authoritative PostgreSQL rows and effective-time windows:

- **BLOCK:** storage remains directed (`source_party_id -> target_party_id`),
  but enforcement checks both endpoint orientations. Either Party's active
  block suppresses interaction between the pair.
- **MUTE:** only the viewer-to-author edge applies. A mute in the reverse
  direction does not suppress the viewer's feed.
- Effective windows use inclusive `valid_from` and exclusive `valid_until`.
  Removed or not-yet-effective/expired relationships do not apply.
- Every lookup is tenant-qualified. Self-pairs are treated as not blocked/muted.
- Feed suppression combines symmetric block and viewer-owned mute. It is a
  filtering primitive, not a complete visibility decision.

Consumers must call the policy service before interactions and before
delivering feed items. They must still enforce the owning capability's
authorization, profile visibility, consent, moderation and data-retention
policies. The policy service is not an unrestricted public discovery API.
Policy decisions use PostgreSQL, never an eventually consistent graph
projection.


## M2e privacy-aware relationship reads

The application provides bounded `following` and `followers` reads over
`FOLLOW` edges. These are relationship-list primitives, not public profile or
discovery endpoints.

- Every SQL query is tenant-qualified and only returns active, effective
  `FOLLOW` edges.
- Ordering is deterministic newest-first by `created_at DESC, relationship_id
  DESC`; V5 adds matching partial indexes for both directions.
- Keyset cursors are opaque Base64URL values bound to tenant, list owner and
  direction. They are continuation markers, not authorization credentials;
  callers must authenticate and authorize every request independently.
- Page size is bounded to 1–100. The cursor advances past scanned rows even
  when privacy policy filters some rows, so hidden records are not repeatedly
  scanned and are not exposed through counts.
- The host must provide `SocialGraphRelationshipVisibilityPort`. It is a
  fail-closed boundary for profile visibility, consent, moderation and
  tenant-sharing policy. The relationship edge itself never grants profile
  visibility.
- The list owner must be visible to the viewer before a followers list is
  queried. Each returned Party is independently checked for visibility and
  for a block in either direction.
- Results contain only canonical Party IDs and relationship timestamps;
  profile content, contact details and other PII are resolved by their owning
  capabilities after their own authorization checks.

The current read service intentionally does not expose blocker/mute lists,
mutual connections, arbitrary graph traversal, follower counts or public
search. Those require separate privacy and authorization contracts.


## M2f interaction and delivery policy gate

The application exposes `SocialGraphInteractionPolicyService` for consuming capabilities that need a single, typed policy decision before acting or delivering content. Its supported intents are `FOLLOW`, `MESSAGE`, `COMMENT`, `INVITE`, `ENDORSE`, `FEED_DELIVERY` and `NOTIFICATION_DELIVERY`.

- Every intent checks symmetric BLOCK policy first. A block in either stored direction returns `BLOCKED`.
- Only `FEED_DELIVERY` and `NOTIFICATION_DELIVERY` consult directional MUTE policy. A viewer-owned mute returns `MUTED`; it does not deny comments, messages, invitations, endorsements or relationship commands by itself.
- Non-delivery intents return `ALLOWED` when no block applies. `ALLOWED` is only a Social Graph relationship-policy result; the consumer must still perform its own action authorization and domain validation.
- Both relationship lookups in one evaluation use the same captured instant, avoiding mixed effective-time decisions.
- Self-pairs return `ALLOWED` without a relationship-store lookup.
- Suppression is a delivery/read decision, not event deletion. Notification and activity outbox records, audit history and source events remain intact; consumers should suppress dispatch while retaining durable intent and observability.
- This is an application contract, not an implicit dependency from Notification or Feed into Social Graph. Hosts compose an adapter at the boundary and must supply canonical tenant-scoped Party IDs from trusted identity/domain records. Do not parse Party IDs from untrusted notification text or source-reference strings.

The consumer still owns profile visibility, consent, moderation, notification preferences unrelated to Social Graph, recipient eligibility, rate limits and transport/provider behavior. No feed API, message service, notification mutation or public discovery endpoint is introduced by this increment.
