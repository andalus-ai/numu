# M3A.4 — Federated tenant-selection credentials

## Purpose

Federated authentication must enter the same tenant-selection exchange as local-password login. A successful OIDC, LDAP, or SAML authentication proves the user's identity; it does not select or authorize a tenant.

## Implemented in this slice

- `FederatedAuthenticationService.completeForTenantSelection` provides an OIDC completion path that preserves the existing JIT provisioning and group synchronization, then issues a short-lived `token_use=tenant_selection` credential through `TenantTokenIssuerPort`.
- `TenantFederatedAuthResource` exposes `GET /api/v2/auth/federated/providers`, `GET /api/v2/auth/federated/{providerId}/authorize`, and `GET /api/v2/auth/federated/{providerId}/callback`. It uses a separate HttpOnly, SameSite=Lax flow cookie and leaves the legacy v1 federation callback unchanged.
- `DirectoryAuthenticationService.loginForTenantSelection` and `SamlAuthenticationService.consumeForTenantSelection` provide matching application-service paths after their respective protocol adapters verify credentials/assertions.
- The federated selection token carries no tenant ID. The user must still call `GET /api/v2/auth/tenants` and `POST /api/v2/auth/tenant-exchange`; exchange rechecks active membership before issuing tenant-bound access.

## Provider configuration requirement

For OIDC providers using the new flow, the registered redirect URI must be configured as:

`/api/v2/auth/federated/{providerId}/callback`

The exact externally reachable origin and path must match the provider registration and the provider's configured callback URI. Do not change existing v1 provider registrations as part of this migration; the v1 callback continues to issue its legacy credential.

## Security invariants

- Protocol verification remains in the OIDC/LDAP/SAML adapters.
- JIT user resolution and group-to-role synchronization remain in the existing federation application service.
- Tenant-selection credentials are minted only after a verified federated identity resolves to an active internal user.
- No tenant is inferred from provider groups, email domain, organization name, or a client-supplied tenant ID.
- The selection credential remains restricted by `TenantSelectionCredentialFilter`; it cannot be used as a general API or gRPC business credential.

## Remaining before production rollout

1. HTTP-level tests now cover public provider discovery plus callback rejection for missing parameters, missing/malformed flow cookies, state mismatch, and sanitized provider errors. Successful selection-token issuance is covered at the application-service boundary; a full provider-backed authorize/callback round trip remains an integration-test follow-up.
2. Add inbound REST resources for LDAP and SAML tenant-selection flows only if those login modes are enabled as interactive public endpoints; their application-service entry points are implemented, but this slice does not add public LDAP/SAML routes.
3. Execute the identity application and adapter Maven test reactors, including PostgreSQL/Testcontainers and Quarkus HTTP tests, on the exact branch head.
4. Verify each IdP redirect URI, issuer/audience configuration, key rotation, and downstream enforcement of `token_use=access` for tenant-bound APIs.
5. Migrate clients deliberately; do not silently replace the existing v1 federation token contract.
