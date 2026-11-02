package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;
import tech.kayys.numou.security.vault.spi.SecretLifecyclePort;
import tech.kayys.numou.security.vault.spi.SecretRepositoryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Tenant-scoped use cases for versioned secrets. Authorization always precedes
 * storage access; implementations never expose plaintext as a String.
 */
public final class VaultSecretService {
    private static final String RESOURCE = "vault-secret";

    private final AuthorizationService authorization;
    private final SecretRepositoryPort secrets;
    private final SecretLifecyclePort lifecycle;
    private final SecretEncryptionPort encryption;
    private final Clock clock;

    public VaultSecretService(AuthorizationService authorization, SecretRepositoryPort secrets,
                              SecretLifecyclePort lifecycle, SecretEncryptionPort encryption, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.secrets = Objects.requireNonNull(secrets, "secrets is required");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle is required");
        this.encryption = Objects.requireNonNull(encryption, "encryption is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /**
     * Creates the first version or appends a new immutable version. The caller
     * retains ownership of plaintext; a short-lived defensive copy is wiped
     * immediately after synchronous encryption.
     */
    public Uni<EncryptedSecret> put(SecurityContext context, VaultId vaultId, SecretId secretId,
                                    char[] plaintext, Instant expiresAt) {
        Objects.requireNonNull(plaintext, "plaintext is required");
        if (plaintext.length == 0) return Uni.createFrom().failure(new IllegalArgumentException("secret must not be empty"));
        if (expiresAt != null && !expiresAt.isAfter(clock.instant()))
            return Uni.createFrom().failure(new IllegalArgumentException("expiresAt must be in the future"));

        return authorization.require(context, ActionType.UPDATE, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status == SecretStatus.REVOKED)
                        return Uni.createFrom().failure(new SecretRevokedException());
                    return secrets.findLatest(context.tenantId(), vaultId, secretId);
                })
                .flatMap(latest -> {
                    int previous = latest.map(value -> value.version().value()).orElse(0);
                    int next = Math.addExact(previous, 1);
                    char[] copy = plaintext.clone();
                    final SecretEncryptionPort.EncryptedPayload payload;
                    try {
                        payload = encryption.encrypt(new SecretEncryptionPort.EncryptionScope(
                                context.tenantId(), vaultId, secretId, next), copy);
                    } finally {
                        Arrays.fill(copy, '\0');
                    }
                    return secrets.appendVersion(context.tenantId(), vaultId, secretId, previous, next,
                            payload.algorithm(), payload.formatVersion(), payload.keyId(),
                            payload.ciphertext(), expiresAt);
                });
    }

    /** Reveals one version only after authorization, lifecycle and expiry checks. */
    public Uni<char[]> reveal(SecurityContext context, VaultId vaultId, SecretId secretId, int version) {
        if (version < 1) return Uni.createFrom().failure(new IllegalArgumentException("version must be positive"));
        return authorization.require(context, ActionType.READ, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status != SecretStatus.ACTIVE)
                        return Uni.createFrom().failure(new SecretRevokedException());
                    return secrets.findVersion(context.tenantId(), vaultId, secretId, version);
                })
                .flatMap(found -> {
                    EncryptedSecret record = found.orElseThrow(SecretNotFoundException::new);
                    if (!record.tenantId().equals(context.tenantId())
                            || !record.vaultId().equals(vaultId) || !record.secretId().equals(secretId))
                        return Uni.createFrom().failure(new SecretNotFoundException());
                    if (record.expiresAt() != null && !record.expiresAt().isAfter(clock.instant()))
                        return Uni.createFrom().failure(new SecretExpiredException());
                    return Uni.createFrom().item(encryption.decrypt(
                            new SecretEncryptionPort.EncryptionScope(context.tenantId(), vaultId, secretId, version),
                            record.keyId(), record.encryptionAlgorithm(), record.formatVersion(), record.ciphertext()));
                });
    }

    /** Revocation is a separately authorized, idempotent lifecycle transition. */
    public Uni<Void> revoke(SecurityContext context, VaultId vaultId, SecretId secretId) {
        return authorization.require(context, ActionType.DELETE, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.revoke(context.tenantId(), vaultId, secretId));
    }
}
