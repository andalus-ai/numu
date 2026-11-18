package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.vault.domain.VaultWorkloadIdentity;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves workload identity only from an already verified platform principal.
 * Implementations must not trust caller-provided identity headers or JWT claims
 * that have not passed the platform authentication boundary.
 */
public interface WorkloadIdentityResolverPort {
    Uni<Optional<VaultWorkloadIdentity>> resolve(Principal principal, UUID tenantId);
}
