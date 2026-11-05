package tech.kayys.numou.organization.adapter.jdbc;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.application.InvitationSecretCipherPort;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;
import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultId;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Bridges invitation payload encryption to Numou Vault's authenticated envelope contract. */
public final class VaultInvitationSecretCipherAdapter implements InvitationSecretCipherPort {
    private static final UUID INVITATION_VAULT_NAMESPACE =
            UUID.nameUUIDFromBytes("numou:organization:invitation-delivery:v1".getBytes(StandardCharsets.UTF_8));
    private final SecretEncryptionPort encryption;

    public VaultInvitationSecretCipherAdapter(SecretEncryptionPort encryption) {
        this.encryption = Objects.requireNonNull(encryption);
    }

    @Override public EncryptedSecret encrypt(TenantId tenantId, OrganizationInvitationId invitationId,
                                               String plaintext) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("Secret is required");
        VaultId vaultId = VaultId.of(INVITATION_VAULT_NAMESPACE);
        SecretId secretId = SecretId.of(invitationId.value());
        char[] chars = plaintext.toCharArray();
        try {
            SecretEncryptionPort.EncryptedPayload encrypted = encryption.encrypt(
                    new SecretEncryptionPort.EncryptionScope(tenantId.value(), vaultId, secretId, 1), chars);
            return new EncryptedSecret(encrypted.algorithm(), encrypted.formatVersion(), encrypted.keyId(),
                    vaultId.value().toString(), secretId.value().toString(), encrypted.ciphertext());
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    @Override public String decrypt(TenantId tenantId, OrganizationInvitationId invitationId,
                                     EncryptedSecret encrypted) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(invitationId);
        Objects.requireNonNull(encrypted);
        UUID expectedVault = INVITATION_VAULT_NAMESPACE;
        UUID expectedSecret = invitationId.value();
        if (!expectedVault.toString().equals(encrypted.vaultId())
                || !expectedSecret.toString().equals(encrypted.secretId()))
            throw new SecurityException("Invitation secret scope mismatch");
        char[] clear = encryption.decrypt(
                new SecretEncryptionPort.EncryptionScope(tenantId.value(), VaultId.of(expectedVault),
                        SecretId.of(expectedSecret), 1),
                encrypted.keyId(), encrypted.algorithm(), encrypted.formatVersion(), encrypted.ciphertext());
        try { return new String(clear); }
        finally { Arrays.fill(clear, '\0'); }
    }
}
