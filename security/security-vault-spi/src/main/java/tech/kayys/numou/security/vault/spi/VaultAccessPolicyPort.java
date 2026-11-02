package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;

/**
 * Host-owned policy decision point. Implementations must fail closed, audit
 * denials without secret values, and validate workload audience/issuer/scope
 * as well as tenant, vault, operation and resource boundaries.
 */
public interface VaultAccessPolicyPort {
    /** Completes only when policy allows the exact request; otherwise fails. */
    Uni<Void> authorize(VaultAccessRequest request);
}
