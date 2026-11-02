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

/** Tenant-scoped secret use cases. Plaintext is never represented as a String. */
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

    /** Creates version 1. The caller retains ownership of the supplied array. */
    public Uni<EncryptedSecret> create(SecurityContext context, VaultId vaultId, SecretId secretId,
                                       char[] plaintext, Instant expiresAt) {
        validate(plaintext, expiresAt);
        return authorization.require(context, ActionType.CREATE, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status.isPresent()) return Uni.createFrom().failure(new SecretAlreadyExistsException());
                    return secrets.findLatest(context.tenantId(), vaultId, secretId);
                })
                .flatMap(latest -> {
                    if (latest.isPresent()) return Uni.createFrom().failure(new SecretAlreadyExistsException());
                    return encryptAndAppend(context, vaultId, secretId, 0, 1, plaintext, expiresAt)
                            .flatMap(saved -> authorization.auditPerformed(context, "VAULT_SECRET_CREATED",
                                    RESOURCE, secretId.toString(), "version=1").replaceWith(saved));
                });
    }

    /** Appends a new immutable version using compare-and-append concurrency control. */
    public Uni<EncryptedSecret> rotate(SecurityContext context, VaultId vaultId, SecretId secretId,
                                       char[] plaintext, Instant expiresAt) {
        validate(plaintext, expiresAt);
        return authorization.require(context, ActionType.UPDATE, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status.orElse(SecretStatus.ACTIVE) == SecretStatus.REVOKED)
                        return Uni.createFrom().failure(new SecretRevokedException());
                    return secrets.findLatest(context.tenantId(), vaultId, secretId);
                })
                .flatMap(latest -> {
                    if (latest.isEmpty()) return Uni.createFrom().failure(new SecretNotFoundException());
                    int previous = latest.get().version().value();
                    int next = Math.addExact(previous, 1);
                    return encryptAndAppend(context, vaultId, secretId, previous, next, plaintext, expiresAt)
                            .flatMap(saved -> authorization.auditPerformed(context, "VAULT_SECRET_ROTATED",
                                    RESOURCE, secretId.toString(), "version=" + next).replaceWith(saved));
                });
    }

    /** Reveals a version only after authorization, lifecycle and expiry checks. */
    public Uni<char[]> reveal(SecurityContext context, VaultId vaultId, SecretId secretId, int version) {
        if (version < 1) return Uni.createFrom().failure(new IllegalArgumentException("version must be positive"));
        return authorization.require(context, ActionType.READ, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status.orElse(SecretStatus.ACTIVE) != SecretStatus.ACTIVE)
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
                    char[] plaintext = encryption.decrypt(
                            new SecretEncryptionPort.EncryptionScope(context.tenantId(), vaultId, secretId, version),
                            record.keyId(), record.encryptionAlgorithm(), record.formatVersion(), record.ciphertext());
                    return authorization.auditPerformed(context, "VAULT_SECRET_REVEALED", RESOURCE,
                                    secretId.toString(), "version=" + version)
                            .replaceWith(plaintext)
                            .onFailure().invoke(failure -> Arrays.fill(plaintext, '\\0'));
                });
    }

    public Uni<Void> revoke(SecurityContext context, VaultId vaultId, SecretId secretId) {
        return authorization.require(context, ActionType.DELETE, RESOURCE, secretId.toString())
                .flatMap(ignored -> lifecycle.revoke(context.tenantId(), vaultId, secretId))
                .flatMap(ignored -> authorization.auditPerformed(context, "VAULT_SECRET_REVOKED",
                        RESOURCE, secretId.toString(), "revoked=true"));
    }

    private Uni<EncryptedSecret> encryptAndAppend(SecurityContext context, VaultId vaultId, SecretId secretId,
                                                   int previous, int version, char[] plaintext, Instant expiresAt) {
        char[] copy = plaintext.clone();
        final SecretEncryptionPort.EncryptedPayload payload;
        try {
            payload = encryption.encrypt(new SecretEncryptionPort.EncryptionScope(
                    context.tenantId(), vaultId, secretId, version), copy);
        } finally {
            Arrays.fill(copy, '\\0');
        }
        return secrets.appendVersion(context.tenantId(), vaultId, secretId, previous, version,
                payload.algorithm(), payload.formatVersion(), payload.keyId(), payload.ciphertext(), expiresAt);
    }

    private void validate(char[] plaintext, Instant expiresAt) {
        if (plaintext == null || plaintext.length == 0)
            throw new IllegalArgumentException("secret must not be empty");
        if (expiresAt != null && !expiresAt.isAfter(clock.instant()))
            throw new IllegalArgumentException("expiresAt must be in the future");
    }
}
