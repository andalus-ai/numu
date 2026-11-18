package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.SecretStatus;
import tech.kayys.numou.security.vault.domain.VaultId;

import java.util.UUID;
import java.util.Optional;

/** Atomic lifecycle operations for a tenant-scoped logical secret. */
public interface SecretLifecyclePort {
    Uni<Optional<SecretStatus>> status(UUID tenantId, VaultId vaultId, SecretId secretId);
    /** Revocation is idempotent and must prevent subsequent reveal operations. */
    Uni<Void> revoke(UUID tenantId, VaultId vaultId, SecretId secretId, VaultAuditEntry auditEntry);
}
