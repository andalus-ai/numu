package tech.kayys.numou.security.vault.spi;

import tech.kayys.numou.security.vault.domain.SecretId;
import tech.kayys.numou.security.vault.domain.VaultId;

import java.util.UUID;

/** Cryptographic boundary. Implementations must use authenticated encryption and envelope keys. */
public interface SecretEncryptionPort {
    EncryptedPayload encrypt(EncryptionScope scope, char[] plaintext);
    char[] decrypt(EncryptionScope scope, String keyId, String algorithm, int formatVersion, byte[] ciphertext);

    record EncryptionScope(UUID tenantId, VaultId vaultId, SecretId secretId, int version) {
        public EncryptionScope {
            if (tenantId == null || vaultId == null || secretId == null || version < 1)
                throw new IllegalArgumentException("complete positive encryption scope is required");
        }
    }

    record EncryptedPayload(String algorithm, int formatVersion, String keyId, byte[] ciphertext) {
        public EncryptedPayload {
            if (algorithm == null || algorithm.isBlank() || keyId == null || keyId.isBlank()
                    || formatVersion < 1 || ciphertext == null || ciphertext.length == 0)
                throw new IllegalArgumentException("valid encrypted payload is required");
            ciphertext = ciphertext.clone();
        }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public String toString() {
            return "EncryptedPayload[algorithm=" + algorithm + ", formatVersion=" + formatVersion
                    + ", keyId=" + keyId + ", ciphertext=[REDACTED]]";
        }
    }
}
