package tech.kayys.numou.notification.persistence.jdbc;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.notification.application.port.PushTokenProtector;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmPushTokenProtectorTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    @Test
    void encryptsAndDecryptsWithVersionedKeyIdentifier() {
        SecretKey oldKey = key((byte) 1);
        SecretKey newKey = key((byte) 2);
        MutableKeys keys = new MutableKeys(oldKey, newKey);
        AesGcmPushTokenProtector protector = new AesGcmPushTokenProtector(keys);

        PushTokenProtector.EncryptedToken oldEnvelope = protector.protect(tenant, recipient, "device-secret");
        assertEquals("key-v1", oldEnvelope.keyId());
        keys.active = new PushTokenKeyProvider.KeyMaterial("key-v2", newKey);
        PushTokenProtector.EncryptedToken newEnvelope = protector.protect(tenant, recipient, "device-secret");

        assertEquals("key-v2", newEnvelope.keyId());
        assertEquals("device-secret", protector.reveal(tenant, recipient, oldEnvelope.keyId(), oldEnvelope.ciphertext()));
        assertEquals("device-secret", protector.reveal(tenant, recipient, newEnvelope.keyId(), newEnvelope.ciphertext()));
        assertNotEquals(oldEnvelope.ciphertext(), newEnvelope.ciphertext());
        assertFalse(oldEnvelope.toString().contains(oldEnvelope.ciphertext()));
    }

    @Test
    void authenticatedDataPreventsCiphertextBeingMovedAcrossOwners() {
        AesGcmPushTokenProtector protector = new AesGcmPushTokenProtector(new MutableKeys(key((byte) 3), key((byte) 4)));
        PushTokenProtector.EncryptedToken envelope = protector.protect(tenant, recipient, "device-secret");

        assertThrows(IllegalStateException.class, () -> protector.reveal(
                UUID.randomUUID(), recipient, envelope.keyId(), envelope.ciphertext()));
        assertThrows(IllegalStateException.class, () -> protector.reveal(
                tenant, UUID.randomUUID(), envelope.keyId(), envelope.ciphertext()));
    }

    @Test
    void unknownKeyAndTamperedCiphertextFailClosed() {
        AesGcmPushTokenProtector protector = new AesGcmPushTokenProtector(new MutableKeys(key((byte) 5), key((byte) 6)));
        PushTokenProtector.EncryptedToken envelope = protector.protect(tenant, recipient, "device-secret");
        byte[] tampered = java.util.Base64.getDecoder().decode(envelope.ciphertext());
        tampered[tampered.length - 1] ^= 1;

        assertThrows(IllegalStateException.class, () -> protector.reveal(tenant, recipient, "missing-key", envelope.ciphertext()));
        assertThrows(IllegalStateException.class, () -> protector.reveal(tenant, recipient, envelope.keyId(),
                java.util.Base64.getEncoder().encodeToString(tampered)));
    }

    private static SecretKey key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return new SecretKeySpec(bytes, "AES");
    }

    private static final class MutableKeys implements PushTokenKeyProvider {
        private KeyMaterial active;
        private final Map<String, SecretKey> keys;

        private MutableKeys(SecretKey first, SecretKey second) {
            this.active = new KeyMaterial("key-v1", first);
            this.keys = Map.of("key-v1", first, "key-v2", second);
        }
        public KeyMaterial activeKey() { return active; }
        public Optional<SecretKey> keyFor(String keyId) { return Optional.ofNullable(keys.get(keyId)); }
    }
}
