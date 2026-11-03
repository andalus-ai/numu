# Vault application services

The vault application layer composes two independent authorization boundaries for every secret operation:

1. The platform `AuthorizationService` checks the generic principal/tenant/resource/action grant.
2. The host-provided `VaultAccessPolicyPort` evaluates vault-specific policy using the verified principal, tenant, roles, vault/secret scope, operation, and any resolved workload identity.

The application layer requires both ports at construction. There is no permissive fallback policy. A missing policy implementation is a composition error; a policy denial or resolver failure prevents secret reads and mutations.

## Workload identity trust boundary

`WorkloadIdentityResolverPort` resolves an optional workload identity from the platform's already verified `Principal` and tenant. It must not accept identity claims directly from request headers or unverified tokens. The returned identity is tenant-bound, has bounded issuer/subject/audience/scope claims, and is rejected when expired or bound to another tenant. `VaultWorkloadIdentity.permits(...)` provides a deterministic claim-policy predicate for exact trusted-issuer, required-audience, tenant, expiry and operation-scope checks. It is not a token verifier: the host adapter must authenticate the issuer and validate the credential signature, trust chain, token status and subject mapping before constructing this value.

The vault does not implement token issuance, OIDC federation, SPIFFE/SPIRE, cloud IAM exchange, or credential validation. Those belong in host authentication and identity adapters. A host may resolve a workload identity to no result for a human principal; its policy implementation still decides whether that human may perform the requested operation.

The domain VaultPolicyEvaluator supplies deterministic default-deny semantics and deny-overrides-allow behavior for one immutable policy snapshot. The PostgreSQL adapter persists tenant-local revisions, uses optimistic publication, loads a consistent snapshot, and durably records both allow and deny decisions before returning. Policy-store, publication-audit, and decision-audit failures fail closed. The host still owns policy-administration authorization and the stable mapping from verified platform principals to vault policy subject IDs.

Policy implementations should enforce:
- tenant and vault boundary;
- operation-specific scope (`CREATE`, `READ`, `ROTATE`, `REVOKE`);
- workload issuer, subject, audience, expiry and scope where present;
- least privilege, environment separation and explicit break-glass policy;
- policy version and decision audit metadata without secret values.

The platform grant check is not replaced by vault policy, and vault policy is not replaced by possession of a secret identifier. Policy adapters should be deterministic, observable, and tested against cross-tenant and cross-vault attempts. This milestone does not ship OIDC, SPIFFE/SPIRE or cloud-IAM provider adapters, a public policy-administration API, or break-glass workflows. Those require a selected host identity provider, host-authorized administration surface, and auditable operator model rather than fabricated provider-specific implementations.


## Policy administration

Policy publication is exposed through `VaultPolicyAdministrationService`, which requires the platform `AuthorizationService` grant for the active tenant's `vault-policy` resource before calling the repository. The caller supplies the expected active revision and a complete replacement rule set; the repository validates tenant/revision binding and performs compare-and-set publication with its administrative audit fact in one transaction. A host API must still authenticate the operator, grant only designated security administrators the policy-update permission, and expose no direct database write path.


Policy publication and rollback require two different verified principals in the same tenant. The proposer needs the `vault-policy` UPDATE grant; the approver needs a separate `vault-policy-approve` or `vault-policy-rollback-approve` PERFORM grant. The application compares stable principal IDs, and persistence records both human-readable actor references. A host must provision these grants to separate security-administrator roles. Rollback republishes an immutable historical snapshot as a new revision and never rewrites history.

Operational metrics are exposed through `VaultPolicyMetricsService`, require the `vault-policy-metrics` READ grant, and are limited to a seven-day window.
