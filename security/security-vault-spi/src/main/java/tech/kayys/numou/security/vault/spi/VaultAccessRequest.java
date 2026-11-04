package tech.kayys.numou.security.vault.spi;

import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.vault.domain.VaultId;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultOperation;
import tech.kayys.numou.security.vault.domain.VaultWorkloadIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Complete, verified policy input. The caller cannot supply an unverified principal. */
public record VaultAccessRequest(UUID tenantId, Principal principal, Set<String> roles,
                                 VaultId vaultId, SecretId secretId, VaultOperation operation,
                                 Optional<VaultWorkloadIdentity> workloadIdentity) {
    public VaultAccessRequest {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principal, "principal is required");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        Objects.requireNonNull(vaultId, "vaultId is required");
        Objects.requireNonNull(secretId, "secretId is required");
        Objects.requireNonNull(operation, "operation is required");
        workloadIdentity = workloadIdentity == null ? Optional.empty() : workloadIdentity;
        workloadIdentity.ifPresent(identity -> {
            if (!identity.tenantId().equals(tenantId))
                throw new IllegalArgumentException("workload identity tenant mismatch");
        });
    }
}
