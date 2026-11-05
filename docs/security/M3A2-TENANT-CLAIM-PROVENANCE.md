# M3A.2 — Tenant claim provenance and tenant-selection gate

Status: source-traced; implementation intentionally blocked pending an explicit tenant-selection contract.

## Scope

This note records the source-level trace for the tenant claim consumed by
`IdentityManagementGuard.requireTenantPermission` on the
`security/m1c3-tenant-policy-bridge` branch. It is not a claim that JWT
issuance, request authentication, or tenant authorization is production-ready.

## Verified source facts

1. `AuthResource.login` delegates local credential login to
   `TokenService.loginLocal`.
2. `TokenService.loginLocal` authenticates a user by email/password and calls
   `TokenIssuerPort.issueToken(userId, "USER", displayName)`.
3. `TokenIssuerPort` accepts only principal ID, principal type, and display
   name. It has no tenant ID, tenant-selection proof, or membership decision
   parameter.
4. `TokenService.issueFor` has the same tenant-free issuer contract.
5. `application.properties` configures the JWT issuer and public-key
   verification, but the source inspected here does not establish that the
   local token issuer emits `numou_tenant_id`.
6. `IdentityManagementGuard.requireTenantPermission` reads
   `numou_tenant_id` from Quarkus `SecurityIdentity`, parses it as a
   `TenantId`, checks active membership through `TenantMembershipResolver`,
   then calls `AuthorizationServicePort`.

## Security conclusion

A signed JWT claim is trustworthy only when its issuer and issuance rules are
trusted. The membership check prevents a non-member from using a tenant claim,
but it does not by itself prove that the token issuer intended the selected
tenant, nor does it make a tenant-free local login token tenant-scoped.

The existing local login flow cannot explicitly bind the authenticated session
to a selected tenant through its current `TokenIssuerPort` contract. Do not
silently derive a tenant from a request header, query parameter, request body,
or first-membership heuristic. Do not add a default tenant.

## Required design before implementation

Introduce an explicit tenant-selection/token-exchange flow:

1. Authenticate user credentials without accepting a tenant as proof of access.
2. Return or expose the user's eligible tenant choices from the authoritative
   Identity membership capability.
3. Accept an explicit tenant selection in a separate authenticated exchange.
4. Re-check active membership for the authenticated principal and selected
   tenant server-side.
5. Issue a short-lived access token whose signed tenant claim is created only
   after that membership check. The issuer port must represent the tenant
   binding explicitly (or a separate tenant-bound token issuance port must).
6. Ensure refresh, federated login, service-account/client-credentials, PAT,
   and token-exchange paths have explicit and non-conflicting tenant semantics.
   A user token contract must not be reused blindly for machine credentials.
7. Add request-level tests proving forged/untrusted tenant input is ignored,
   missing tenant context is denied, inactive membership is denied, and a
   valid signed tenant-bound token still requires active membership and
   permission.

## Exit criteria

- The concrete JWT issuer implementation and every issuance path are traced.
- Tenant-bound issuance is backed by an active membership check.
- The verified tenant claim is produced by the trusted issuer, not copied from
  caller-controlled input.
- Security guard tests cover 401/403/503 behavior and authorization failure.
- No service-account tenant CRUD is enabled until these checks pass.

## Deliberately not changed

No JWT claim is added, no request header is trusted, no tenant is inferred,
and no service-account endpoint is switched to tenant-scoped behavior in this
source-trace step. The issuer adapter implementation and runtime request
identity wiring must be inspected before modifying the token contract.
