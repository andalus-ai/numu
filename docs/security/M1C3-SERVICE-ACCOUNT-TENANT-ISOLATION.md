# M1c.3 — Service-account tenant isolation boundary

Status: implementation contract; source integration remains gated on a trusted tenant-context provider.

## Problem confirmed on main

The current service-account flow is not tenant-safe:

- `ServiceAccountService.create` calls `ServiceAccount.create(..., null, ...)`.
- Duplicate-name checks use `existsByName(name)` without tenant scope.
- Disable uses `findById(id)` without tenant scope.
- Listing uses `findAll()` and is global.
- Service-account outbox events are appended with a null tenant.
- `service_accounts.tenant_id` is non-null in the initial schema, so null creation is also inconsistent with persistence.

The domain aggregate already carries a `TenantId`; do not introduce a second tenant field or a parallel service-account model.

## Trusted tenant-context contract

A service-account command/query must receive a resolved tenant context that is established from authenticated server-side identity and an authorization decision.

The context must contain at least:

- canonical authenticated actor ID;
- canonical tenant ID;
- the authorization decision/scope used for the operation.

The context provider must verify that the actor is an active member or otherwise explicitly authorized principal in that tenant, and that the requested permission applies within that tenant. A globally configured management permission is not, by itself, proof of tenant membership.

### Prohibited tenant sources

- Never accept `X-Tenant-ID`, query parameters, request JSON, or an unverified token claim as sufficient authority to select a tenant.
- Never infer a user's tenant from a single-membership assumption.
- Never use an operator/global scope as an implicit tenant.
- Never fall back to unscoped repository methods when tenant context is absent.

If tenant context cannot be established, reject the request before invoking the application service.

## Required application and persistence changes

Once a trusted tenant-context provider is available:

1. Make tenant ID a required input to service-account creation and reject null tenant IDs in the aggregate.
2. Scope duplicate-name checks by tenant.
3. Replace ID-only disable lookup with a tenant-and-ID lookup.
4. Replace global listing with tenant-scoped listing.
5. Ensure every service-account persistence query includes tenant ID in its predicate.
6. Include tenant ID in outbox append metadata and emitted domain-event envelopes.
7. Preserve the existing non-null tenant column and tenant/name uniqueness boundary; add/adjust indexes only from measured query needs.
8. Remove or deprecate unscoped repository methods after all callers have migrated. Do not retain them as a fallback.

Suggested repository contract:

- `existsByTenantIdAndName(tenantId, name)`
- `findByTenantIdAndId(tenantId, id)`
- `findAllByTenantId(tenantId)`
- `save(serviceAccount)` with tenant immutable after creation

Names are illustrative; align with the repository's established naming conventions.

## Required security and regression tests

- A request without a verified tenant context is rejected.
- A caller-supplied `X-Tenant-ID` cannot establish or override tenant context.
- An actor who belongs to tenant A cannot list tenant B's service accounts.
- An actor who belongs to tenant A cannot disable a tenant B service account even when its UUID is known.
- Duplicate names are rejected within one tenant but may exist in different tenants if the domain policy permits.
- Creation persists the verified tenant ID and emits an outbox record carrying the same tenant ID.
- Tenant ID cannot be changed by update/reconstitution paths.
- Existing USER/SERVICE principal distinctions remain intact; service-account records are not OAuth application credentials.

## Integration gate

Before implementing the application/resource/persistence changes, identify and test the canonical tenant-membership authorization source already used by the platform. If no such source exists, add it as a separately reviewed identity capability first. Do not substitute a header filter or duplicate membership store.

## Related source

- `IdentityManagementGuard` currently resolves permissions against a configured management scope and returns actor UUID; it does not resolve tenant membership.
- `VerifiedActorFilter` canonicalizes actor identity; it does not establish tenant context.
- `ServiceAccount` already contains `TenantId`.
- `ServiceAccountRepository` currently exposes unscoped read/exists methods.
