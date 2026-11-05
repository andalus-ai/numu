# M3A.3 — Tenant-bound authentication implementation gate

**Status:** Implementation boundary correction and source-trace; tenant-bound human access-token issuance remains blocked pending the contract decisions below.

## Changes in this milestone

- Remove `IdentityTenantMembershipResolver`, which duplicated the existing `IdentityTenantMembershipAdapter` implementation of the same Security SPI.
- Retain the existing adapter and its tests as the single Identity-owned bridge from `TenantMembershipResolver` to `MembershipManagementPort`.
- Record the verified login/token-issuance paths and the remaining prerequisites for a safe tenant-selection exchange.

## Verified source paths

| Concern | Source | Observed behavior |
|---|---|---|
| Local login | `identity/numou-identity-adapter/.../inbound/rest/AuthResource.java` | `POST /api/v1/auth/login` delegates to `TokenService.loginLocal`. |
| Local authentication | `identity/numou-identity-application/.../service/TokenService.java` | Validates active user and password, then calls the tenant-free `TokenIssuerPort.issueToken(userId, "USER", displayName)`. |
| Human JWT issuer | `identity/numou-identity-adapter/.../outbound/security/SmallRyeJwtTokenAdapter.java` | Issues subject, UPN, principalType, empty groups and expiry. It does not issue a tenant claim, token purpose, or audience. |
| JWT verification | Same adapter | Parses and validates the JWT, but `VerifiedToken` currently exposes only principal type, principal ID and display name. |
| Machine-client JWT issuer | `identity/numou-identity-adapter/.../outbound/security/SmallRyeOAuthAccessTokenIssuer.java` | Issues audience, `tenant_id`, `application_id`, scopes, credential version and `token_use=access`. |
| Tenant guard | `identity/numou-identity-adapter/.../inbound/security/IdentityManagementGuard.java` | Reads the configured tenant claim, defaulting to `numou_tenant_id`, then checks membership and tenant-scoped authorization. |
| Identity configuration | `identity/numou-identity-adapter/src/main/resources/application.properties` | Does not override `numou.security.tenant-claim`; therefore the guard's default is active in this configuration. |
| Membership authority | `identity/numou-identity-application/.../port/MembershipManagementPort.java` | Exposes only `isActive(tenantId, userId)`; it does not enumerate a user's eligible tenants. |
| Existing membership bridge | `identity/numou-identity-adapter/.../outbound/security/IdentityTenantMembershipAdapter.java` | Already implements the Security SPI and has focused tests. |

## Concrete issues that must be resolved before enabling tenant-bound human tokens

1. **Duplicate CDI SPI implementation.** The merged tenant-policy bridge introduced `IdentityTenantMembershipResolver` alongside the pre-existing `IdentityTenantMembershipAdapter`. Both implement `TenantMembershipResolver` and are CDI beans. Keeping both can make injection ambiguous. This milestone removes the duplicate and retains the already-tested adapter.
2. **Tenant claim mismatch.** The human JWT has no tenant claim. The OAuth client issuer emits `tenant_id`, while `IdentityManagementGuard` defaults to `numou_tenant_id`. The identity configuration inspected does not align these names. A single canonical claim must be selected and used by issuers, validators, guards, gateway policy and downstream APIs.
3. **No eligible-tenant query contract.** `MembershipManagementPort` can validate one tenant/user pair but cannot return all active memberships. A tenant-selection UI needs a server-side eligible-tenant query backed by Identity's existing membership records; it must not create a second membership store or trust client-supplied tenant choices.
4. **No token-purpose separation for human login.** Current local login JWTs have neither a tenant-selection purpose nor a restricted audience. They cannot safely be repurposed as a bootstrap credential without also ensuring they are rejected by ordinary business APIs.
5. **Federation and long-lived credentials need explicit semantics.** Federated login ultimately shares Identity token issuance paths; PAT/remember-me credentials have a separate lifecycle and must not silently become tenant-bound JWTs. Each flow needs an explicit exchange/refresh policy.

## Required M3A.3 contract

The next implementation slice should provide:

1. A short-lived, narrowly-audienced **tenant-selection credential** issued only after successful user authentication. It carries a distinct token purpose and no business tenant authority.
2. An Identity-owned query for active tenant memberships, including only the tenant metadata needed to render choices. The server must derive this list from authoritative membership data.
3. A tenant-exchange endpoint that accepts a selected tenant ID only as a *selection request*, validates the signed selection credential, rechecks active membership, and then issues a short-lived tenant-bound access token.
4. A single canonical tenant claim and token-purpose convention shared with machine-client tokens. Prefer preserving the existing OAuth issuer's `tenant_id` claim unless a broader platform contract requires a coordinated migration.
5. An enforcement rule that tenant-selection credentials cannot access business APIs, service-account administration, or gateway-protected capability APIs. Only the tenant-selection exchange route may accept them.
6. Explicit behavior for refresh, federation, PAT/remember-me, service accounts and client credentials. No implicit first tenant, default tenant, or caller-controlled tenant header.
7. Tests for valid selection, non-member selection, revoked membership, invalid/expired/wrong-purpose token, forged tenant input, missing tenant claim, issuer/claim consistency, and tenant-selection token rejection by ordinary APIs.

## Acceptance criteria

- Exactly one CDI bean implements the Identity-backed `TenantMembershipResolver` in the identity adapter.
- No regular human access token is issued without a verified tenant once the exchange flow is enabled.
- Membership is revalidated at exchange time, even if the selection token was valid when issued.
- Tenant claim and token purpose are consistent across local, federated and machine-client issuance.
- Selection tokens cannot authorize ordinary business requests.
- Unit, adapter and request-level tests demonstrate both allow and deny paths.
- No tenant membership persistence is duplicated.
- CI evidence is reported from the exact resulting commit; no unrun test is described as passing.

## Scope deliberately not changed here

This change does not silently change the existing login response or invalidate existing sessions. Those are compatibility-sensitive behaviors and must be changed together with the tenant-selection endpoint, request enforcement, federation semantics and migration/rollout plan in the complete M3A.3 implementation.
