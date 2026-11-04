package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.application.authz.AuthorizationService;
import tech.kayys.numou.security.application.context.SecurityContext;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.vault.domain.*;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;
import tech.kayys.numou.security.vault.spi.SecretLifecyclePort;
import tech.kayys.numou.security.vault.spi.SecretRepositoryPort;
import tech.kayys.numou.security.vault.spi.VaultAuditEntry;
import tech.kayys.numou.security.vault.spi.VaultAccessRequest;
import tech.kayys.numou.security.vault.spi.VaultAccessPolicyPort;
import tech.kayys.numou.security.vault.spi.WorkloadIdentityResolverPort;

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
    private final VaultAccessPolicyPort accessPolicy;
    private final WorkloadIdentityResolverPort workloadIdentities;
    private final Clock clock;

    public VaultSecretService(AuthorizationService authorization, SecretRepositoryPort secrets,
                              SecretLifecyclePort lifecycle, SecretEncryptionPort encryption,
                              VaultAccessPolicyPort accessPolicy, WorkloadIdentityResolverPort workloadIdentities,
                              Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization is required");
        this.secrets = Objects.requireNonNull(secrets, "secrets is required");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle is required");
        this.encryption = Objects.requireNonNull(encryption, "encryption is required");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy is required");
        this.workloadIdentities = Objects.requireNonNull(workloadIdentities, "workloadIdentities is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    /** Creates version 1. The caller retains ownership of the supplied array. */
    public Uni<EncryptedSecret> create(SecurityContext context, VaultId vaultId, SecretId secretId,
                                       char[] plaintext, Instant expiresAt) {
        validate(plaintext, expiresAt);
        return authorize(context, ActionType.CREATE, VaultOperation.CREATE, vaultId, secretId)
                .flatMap(ignored -> lifecycle.status(context.tenantId(), vaultId, secretId))
                .flatMap(status -> {
                    if (status.isPresent()) return Uni.createFrom().failure(new SecretAlreadyExistsException());
                    return secrets.findLatest(context.tenantId(), vaultId, secretId);
                })
                .flatMap(latest -> {
                    if (latest.isPresent()) return Uni.createFrom().failure(new SecretAlreadyExistsException());
                    return encryptAndAppend(context, vaultId, secretId, 0, 1, plaintext, expiresAt,
                            new VaultAuditEntry(context.actor(), "VAULT_SECRET_CREATED", "version=1", clock.instant()));
                });
    }

    /** Appends a new immutable version using compare-and-append concurrency control. */
    public Uni<EncryptedSecret> rotate(SecurityContext context, VaultId vaultId, SecretId secretId,
                                       char[] plaintext, Instant expiresAt) {
        validate(plaintext, expiresAt);
        return authorize(context, ActionType.UPDATE, VaultOperation.ROTATE, vaultId, secretId)
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
                    return encryptAndAppend(context, vaultId, secretId, previous, next, plaintext, expiresAt,
                            new VaultAuditEntry(context.actor(), "VAULT_SECRET_ROTATED", "version=" + next, clock.instant()));
                });
    }

    /** Reveals a version only after authorization, lifecycle and expiry checks. */
    public Uni<char[]> reveal(SecurityContext context, VaultId vaultId, SecretId secretId, int version) {
        if (version < 1) return Uni.createFrom().failure(new IllegalArgumentException("version must be positive"));
        return authorize(context, ActionType.READ, VaultOperation.READ, vaultId, secretId)
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
                    return secrets.appendAuditEvent(context.tenantId(), vaultId, secretId, version,
                                    new VaultAuditEntry(context.actor(), "VAULT_SECRET_REVEALED",
                                            "version=" + version, clock.instant()))
                            .replaceWith(plaintext)
                            .onFailure().invoke(failure -> Arrays.fill(plaintext, (char) 0));
                });
    }

    public Uni<Void> revoke(SecurityContext context, VaultId vaultId, SecretId secretId) {
        return authorize(context, ActionType.DELETE, VaultOperation.REVOKE, vaultId, secretId)
                .flatMap(ignored -> lifecycle.revoke(context.tenantId(), vaultId, secretId,
                        new VaultAuditEntry(context.actor(), "VAULT_SECRET_REVOKED", "revoked=true", clock.instant())));
    }

    /**
     * Applies generic platform authorization and the vault's host-owned policy.
     * Workload identity is resolved from a verified principal, never request claims.
     */
    private Uni<Void> authorize(SecurityContext context, ActionType action, VaultOperation operation,
                                VaultId vaultId, SecretId secretId) {
        return authorization.require(context, action, RESOURCE, secretId.toString())
                .flatMap(ignored -> workloadIdentities.resolve(context.principal(), context.tenantId()))
                .flatMap(identity -> {
                    if (identity.isPresent() && !identity.get().isValidFor(context.tenantId(), clock.instant()))
                        return Uni.createFrom().failure(new VaultAccessDeniedException());
                    return accessPolicy.authorize(new VaultAccessRequest(context.tenantId(),
                            context.principal(), context.roles(), vaultId, secretId, operation, identity));
                });
    }

    private Uni<EncryptedSecret> encryptAndAppend(SecurityContext context, VaultId vaultId, SecretId secretId,
                                                   int previous, int version, char[] plaintext, Instant expiresAt, VaultAuditEntry auditEntry) {
        char[] copy = plaintext.clone();
        final SecretEncryptionPort.EncryptedPayload payload;
        try {
            payload = encryption.encrypt(new SecretEncryptionPort.EncryptionScope(
                    context.tenantId(), vaultId, secretId, version), copy);
        } finally {
            Arrays.fill(copy, (char) 0);
        }
        return secrets.appendVersion(context.tenantId(), vaultId, secretId, previous, version,
                payload.algorithm(), payload.formatVersion(), payload.keyId(), payload.ciphertext(), expiresAt, auditEntry);
    }

    private void validate(char[] plaintext, Instant expiresAt) {
        if (plaintext == null || plaintext.length == 0)
            throw new IllegalArgumentException("secret must not be empty");
        if (expiresAt != null && !expiresAt.isAfter(clock.instant()))
            throw new IllegalArgumentException("expiresAt must be in the future");
    }
}
