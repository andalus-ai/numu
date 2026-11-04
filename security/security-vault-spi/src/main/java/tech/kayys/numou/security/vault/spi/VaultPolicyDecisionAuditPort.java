package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;

/** Durable decision audit. Callers must await successful completion before allowing access. */
public interface VaultPolicyDecisionAuditPort {
    Uni<Void> append(VaultPolicyDecisionAudit audit);
}
