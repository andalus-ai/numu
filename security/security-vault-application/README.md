# Vault application services

The vault application layer composes two independent authorization boundaries for every secret operation:

1. The platform `AuthorizationService` checks the generic principal/tenant/resource/action grant.
2. The host-provided `VaultAccessPolicyPort` evaluates vault-specific policy using the verified principal, tenant, roles, vault/secret scope, operation, and any resolved workload identity.

The application layer requires both ports at construction. There is no permissive fallback policy. A missing policy implementation is a composition error; a policy denial or resolver failure prevents secret reads and mutations.

## Workload identity trust boundary

`WorkloadIdentityResolverPort` resolves an optional workload identity from the platform's already verified `Principal` and tenant. It must not accept identity claims directly from request headers or unverified tokens. The returned identity is tenant-bound, has bounded issuer/subject/audience/scope claims, and is rejected when expired or bound to another tenant. `VaultWorkloadIdentity.permits(...)` provides a deterministic claim-policy predicate for exact trusted-issuer, required-audience, tenant, expiry and operation-scope checks. It is not a token verifier: the host adapter must authenticate the issuer and validate the credential signature, trust chain, token status and subject mapping before constructing this value.

The vault does not implement token issuance, OIDC federation, SPIFFE/SPIRE, cloud IAM exchange, or credential validation. Those belong in host authentication and identity adapters. A host may resolve a workload identity to no result for a human principal; its policy implementation still decides whether that human may perform the requested operation.

Policy implementations should default deny and should enforce:
- tenant and vault boundary;
- operation-specific scope (`CREATE`, `READ`, `ROTATE`, `REVOKE`);
- workload issuer, subject, audience, expiry and scope where present;
- least privilege, environment separation and explicit break-glass policy;
- policy version and decision audit metadata without secret values.

The platform grant check is not replaced by vault policy, and vault policy is not replaced by possession of a secret identifier. Policy adapters should be deterministic, observable, and tested against cross-tenant and cross-vault attempts. This milestone does not ship OIDC, SPIFFE/SPIRE or cloud-IAM provider adapters, a persisted policy engine, or break-glass workflows; those require a selected host identity provider, authoritative policy store, and auditable operator model rather than fabricated provider-specific implementations.
