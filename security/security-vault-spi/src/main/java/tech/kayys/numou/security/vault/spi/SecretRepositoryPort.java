package tech.kayys.numou.security.vault.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.vault.domain.EncryptedSecret;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultId;

import java.util.Optional;
import java.util.UUID;

/** Persistence boundary. Implementations must enforce tenant-scoped uniqueness and append-only versions. */
public interface SecretRepositoryPort {
    /** Atomically allocates and appends the next version; concurrent writers must not overwrite each other. */
    Uni<EncryptedSecret> appendNextVersion(UUID tenantId, VaultId vaultId, SecretId secretId,
                                           String algorithm, int formatVersion, String keyId,
                                           byte[] ciphertext, java.time.Instant expiresAt);

    Uni<Optional<EncryptedSecret>> findVersion(UUID tenantId, VaultId vaultId,
                                                SecretId secretId, int version);

    Uni<Optional<EncryptedSecret>> findLatest(UUID tenantId, VaultId vaultId, SecretId secretId);
}
