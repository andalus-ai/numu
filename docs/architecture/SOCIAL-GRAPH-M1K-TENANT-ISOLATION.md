# Social Graph M1k — Tenant Isolation and Participation Semantics

**Status:** Implementation note and verification gate  
**Date:** 2026-11-04  
**Scope:** PostgreSQL tenant partitioning for Social Graph connections and command idempotency.

## 1. Established semantics

- Ecosystem `PartyId` identifies a canonical person or organization globally. It is not a tenant identifier.
- A Social Graph connection belongs to exactly one `TenantId`.
- Connection lookup, open-pair uniqueness, audit, idempotency and outbox records are tenant-scoped.
- A Party may be referenced by relationships in more than one tenant without making those relationships visible across tenants.
- The same Party pair may have independent relationship lifecycles in two tenants.
- Tenant membership belongs to an authenticated User account. A `PartyIdentityAssociation` is only a tenant-scoped issuer/subject-to-Party link; its domain contract explicitly says it does not grant tenant membership or authorization.
- A caller must pass the host's authenticated tenant-membership and capability-authorization checks before Social Graph commands execute.

These rules preserve the existing M1 model. They do not introduce cross-tenant relationship federation or global social discovery.

## 2. Database guarantees

The PostgreSQL schema uses composite tenant keys for connection identity and foreign-key relationships. The unique open-pair index includes `tenant_id`. Idempotency keys are also unique only within a tenant.

M1k adds a PostgreSQL integration regression test that deliberately reuses:
- the same `SocialConnectionId`;
- the same requester and recipient Party IDs;
- the same idempotency key;

in two different tenants. It verifies that each tenant resolves only its own connection and idempotency record, and that open-pair lookup remains tenant-scoped.

This is a defense-in-depth test of the database/application contract, not a substitute for authorization.

## 3. Participation and recipient eligibility — unresolved integration boundary

The current command path verifies:
1. the requester has a verified issuer/subject association to a Party in the selected tenant;
2. the canonical Party exists and is active;
3. the authenticated request context is an active member of that tenant and has the required Social Graph permission;
4. lifecycle commands are executed by the correct connection participant.

However, request creation currently checks only that the recipient Party exists and is active. It does not independently prove that the recipient has an eligible authenticated User account with active membership in the same tenant.

Do not use `PartyIdentityAssociationRepository` as a substitute for tenant membership: its own contract explicitly disclaims that meaning. Do not equate `PartyId` and `UserId` by UUID or infer membership from a global Party lookup.

Before tightening recipient eligibility, the owning Identity/Tenancy capabilities must publish a supported contract that safely resolves a Party to one or more eligible tenant principals (or an equivalent explicit participant-registration contract). That contract must account for multiple identities per Party, organizations represented by service identities, revocation, and membership changes. Until then, hosts must treat invitation/request recipient eligibility as a policy gap and avoid exposing public discovery or unrestricted connection requests.

## 4. Explicitly out of scope

- Cross-tenant federation or global connection visibility.
- Inferring user membership from Party identity associations.
- Direct reads of Identity/Tenancy persistence tables from Social Graph.
- Mapping UserId, PrincipalId, OrganizationId and PartyId by UUID equality.
- Neo4j projection or global graph traversal.

## 5. Acceptance criteria

- [x] Preserve canonical global Party identity without making relationship data global.
- [x] Keep connection and idempotency lookup tenant-scoped.
- [x] Add PostgreSQL integration coverage for the same Party pair and IDs in separate tenants.
- [ ] Verify the Social Graph CI run with PostgreSQL/Testcontainers.
- [ ] Design and approve a public Identity/Tenancy participant-eligibility contract before enforcing recipient membership at request time.
- [ ] Add host-level tests for requester and recipient membership/revocation behavior once that contract exists.
