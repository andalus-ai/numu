package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;

/** Host-owned policy decision point. Implementations must default deny. */
public interface VaultAccessPolicyPort {
    Uni<Void> authorize(VaultAccessRequest request);
}
