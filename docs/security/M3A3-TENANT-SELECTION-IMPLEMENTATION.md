# M3A.3 — Tenant selection and tenant-bound human access

**Implementation status:** Local-password tenant-selection flow added on a versioned v2 route. Federated login, refresh/remember-me, PAT, and service-account flows remain explicitly outside this slice.

## Flow

1. `POST /api/v2/auth/login` validates the local username/password against Identity's existing user repository.
2. Identity issues a short-lived signed JWT with:
   - `principalType=USER`
   - `token_use=tenant_selection`
   - dedicated tenant-selection audience
   - no tenant claim
   - empty groups
   - five-minute default lifetime
3. `GET /api/v2/auth/tenants` returns tenant IDs from Identity's existing `tenant_memberships` records, filtered to active memberships and active users, in deterministic order.
4. `POST /api/v2/auth/tenant-exchange` accepts a selected tenant ID as a request, rechecks active membership server-side, then issues a tenant-bound human access JWT.
5. The access JWT includes `tenant_id`, `token_use=access`, a bounded lifetime, and the compatibility claim `numou_tenant_id` while the existing identity guard still defaults to that older claim.
6. `TenantSelectionCredentialFilter` rejects a tenant-selection JWT on every JAX-RS route except the exact tenant discovery and exchange endpoints. The generic `TokenVerifierPort` adapter also rejects `token_use=tenant_selection`, preventing the Identity gRPC `resolvePrincipal` path from treating it as a business credential. Tenant-bound JWT verification extracts `tenant_id` (falling back to the compatibility alias) and the additive gRPC response field returns it to callers.

## Ownership and data

- Identity remains the sole owner of users and tenant memberships.
- `TenantDiscoveryPort` is a read-only application port; the PostgreSQL adapter queries the existing `tenant_memberships` and active user records.
- `MembershipManagementPort.isActive` remains the authoritative exchange-time membership check.
- No tenant table, membership table, or duplicate membership aggregate is introduced.
- The selected tenant ID is never trusted without an active membership check.

## Compatibility

- Existing `/api/v1/auth/login` and existing federation flows are unchanged in this slice.
- The new tenant-aware flow is opt-in through `/api/v2/auth/login`.
- The selection token has no tenant claim and is restricted by a request filter to tenant discovery/exchange.
- The access token carries both `tenant_id` (canonical) and `numou_tenant_id` (temporary compatibility alias for the current identity guard). Remove the alias only after every consumer uses the canonical claim.
- Remember-me is rejected by the v2 tenant-selection login. PAT and refresh semantics need their own tenant-aware exchange design; they must not silently inherit tenant context.
- Federated login still issues the legacy token. A follow-up must route OIDC/SAML/LDAP success through the same tenant-selection credential contract before the tenant-aware flow can be considered complete for all human authentication methods.

## Required verification

- Unit tests: active tenant discovery projection; active membership permits token issuance; inactive/revoked membership does not issue a token; missing tenant is rejected before membership lookup; tenant claim parsing and tenant context propagation through gRPC response.
- PostgreSQL adapter integration test verifies multiple active memberships are returned in UUID order, revoked memberships are excluded, and memberships for inactive users are not exposed.
- JWT tests: selection and access tokens have distinct `token_use`, audiences, claims, and bounded lifetimes.
- Request-level tests: route allowlist unit tests cover path/method restrictions; a Quarkus HTTP test signs real selection/access JWTs, verifies tenant discovery remains reachable, and verifies selection credentials are rejected by the service-account management API before its handler executes.
- CI must run against the exact PR head. No local build or CI success is claimed until evidence is available.

## Remaining before production enablement

1. Run the PostgreSQL and Quarkus HTTP integration tests on the exact PR head and address any environment-specific failures.
2. Integrate OIDC, SAML and LDAP flows with tenant-selection credential issuance.
3. Define refresh/PAT/service-account semantics and align all consumers on `tenant_id`.
4. Confirm the API gateway and downstream resource servers enforce the audience and `token_use=access` contract.
