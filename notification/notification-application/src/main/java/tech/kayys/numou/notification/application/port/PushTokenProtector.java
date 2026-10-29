package tech.kayys.numou.notification.application.port;

import java.util.UUID;

/**
 * Protects provider tokens before persistence and reveals them only for delivery.
 * Implementations must use authenticated encryption and versioned key identifiers.
 */
public interface PushTokenProtector {
    EncryptedToken protect(UUID tenantId, UUID recipientId, UUID deviceId, String plaintextToken);
    String reveal(UUID tenantId, UUID recipientId, UUID deviceId, String keyId, String ciphertext);

    record EncryptedToken(String keyId, String ciphertext) {
        public EncryptedToken {
            if (keyId == null || keyId.isBlank() || keyId.length() > 120)
                throw new IllegalArgumentException("keyId must contain 1..120 characters");
            if (ciphertext == null || ciphertext.isBlank())
                throw new IllegalArgumentException("ciphertext is required");
        }
        @Override public String toString() {
            return "EncryptedToken[keyId=" + keyId + ", ciphertext=[REDACTED]]";
        }
    }
}
