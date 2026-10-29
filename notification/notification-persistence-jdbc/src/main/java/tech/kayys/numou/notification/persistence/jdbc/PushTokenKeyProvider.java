package tech.kayys.numou.notification.persistence.jdbc;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Host-provided key management boundary. Production implementations should use
 * a managed KMS or secret manager, not a key embedded in source or a database.
 */
public interface PushTokenKeyProvider {
    KeyMaterial activeKey();
    Optional<SecretKey> keyFor(String keyId);

    record KeyMaterial(String keyId, SecretKey key) {
        public KeyMaterial {
            if (keyId == null || keyId.isBlank() || keyId.length() > 120)
                throw new IllegalArgumentException("keyId must contain 1..120 characters");
            if (key == null) throw new IllegalArgumentException("key is required");
        }
        @Override public String toString() { return "KeyMaterial[keyId=" + keyId + ", key=[REDACTED]]"; }
    }
}
