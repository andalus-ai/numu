package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationInvitationId;

/** Authenticated encryption boundary for one-time invitation bearer secrets. */
public interface InvitationSecretCipherPort {
    EncryptedSecret encrypt(TenantId tenantId, OrganizationInvitationId invitationId, String plaintext);
    String decrypt(TenantId tenantId, OrganizationInvitationId invitationId, EncryptedSecret encrypted);

    record EncryptedSecret(String algorithm, int formatVersion, String keyId,
                           String vaultId, String secretId, byte[] ciphertext) {
        public EncryptedSecret {
            if (algorithm == null || algorithm.isBlank() || formatVersion < 1
                    || keyId == null || keyId.isBlank() || vaultId == null || vaultId.isBlank()
                    || secretId == null || secretId.isBlank() || ciphertext == null || ciphertext.length == 0)
                throw new IllegalArgumentException("Complete encrypted invitation secret is required");
            ciphertext = ciphertext.clone();
        }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public String toString() {
            return "EncryptedSecret[algorithm=" + algorithm + ", keyId=" + keyId + ", ciphertext=[REDACTED]]";
        }
    }
}
