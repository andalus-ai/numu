package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.vault.domain.EncryptedSecret;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultId;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary. Implementations must enforce tenant-scoped uniqueness and append-only versions. */
public interface SecretRepositoryPort {
    /**
     * Atomically appends a version only when the persisted latest version equals
     * expectedPreviousVersion and newVersion equals expectedPreviousVersion + 1.
     * A mismatch must fail with a concurrency/conflict error, never overwrite.
     * Implementations must scope every lookup and uniqueness constraint by tenant.
     * The adapter must defensively copy ciphertext before retaining it.
     */
    Uni<EncryptedSecret> appendVersion(UUID tenantId, VaultId vaultId, SecretId secretId,
                                      int expectedPreviousVersion, int newVersion,
                                      String algorithm, int formatVersion, String keyId,
                                      byte[] ciphertext, java.time.Instant expiresAt, VaultAuditEntry auditEntry);

    /** Appends a metadata-only audit fact for an existing immutable version. */
    Uni<Void> appendAuditEvent(UUID tenantId, VaultId vaultId, SecretId secretId,
                               int version, VaultAuditEntry auditEntry);

    Uni<Optional<EncryptedSecret>> findVersion(UUID tenantId, VaultId vaultId,
                                                SecretId secretId, int version);

    Uni<Optional<EncryptedSecret>> findLatest(UUID tenantId, VaultId vaultId, SecretId secretId);
}
