package tech.kayys.numou.security.vault.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistable encrypted secret version. Plaintext is deliberately absent from
 * the domain model. Ciphertext accessors return defensive copies.
 */
public final class EncryptedSecret {
    private final UUID tenantId;
    private final VaultId vaultId;
    private final SecretId secretId;
    private final SecretVersion version;
    private final String encryptionAlgorithm;
    private final int formatVersion;
    private final String keyId;
    private final byte[] ciphertext;
    private final Instant createdAt;
    private final Instant expiresAt;

    public EncryptedSecret(UUID tenantId, VaultId vaultId, SecretId secretId,
                           SecretVersion version, String encryptionAlgorithm,
                           int formatVersion, String keyId, byte[] ciphertext,
                           Instant createdAt, Instant expiresAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.vaultId = Objects.requireNonNull(vaultId, "vaultId is required");
        this.secretId = Objects.requireNonNull(secretId, "secretId is required");
        this.version = Objects.requireNonNull(version, "version is required");
        this.encryptionAlgorithm = requireText(encryptionAlgorithm, "encryptionAlgorithm", 80);
        if (formatVersion < 1) throw new IllegalArgumentException("formatVersion must be positive");
        this.formatVersion = formatVersion;
        this.keyId = requireText(keyId, "keyId", 160);
        if (ciphertext == null || ciphertext.length < 1) throw new IllegalArgumentException("ciphertext is required");
        this.ciphertext = ciphertext.clone();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        if (expiresAt != null && !expiresAt.isAfter(createdAt))
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        this.expiresAt = expiresAt;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return value;
    }

    public UUID tenantId() { return tenantId; }
    public VaultId vaultId() { return vaultId; }
    public SecretId secretId() { return secretId; }
    public SecretVersion version() { return version; }
    public String encryptionAlgorithm() { return encryptionAlgorithm; }
    public int formatVersion() { return formatVersion; }
    public String keyId() { return keyId; }
    public byte[] ciphertext() { return ciphertext.clone(); }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }

    @Override public String toString() {
        return "EncryptedSecret[tenantId=" + tenantId + ", vaultId=" + vaultId
                + ", secretId=" + secretId + ", version=" + version
                + ", algorithm=" + encryptionAlgorithm + ", formatVersion=" + formatVersion
                + ", keyId=" + keyId + ", ciphertext=[REDACTED], createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
