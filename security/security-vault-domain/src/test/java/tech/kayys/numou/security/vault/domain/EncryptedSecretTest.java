package tech.kayys.numou.security.vault.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedSecretTest {
    @Test
    void ciphertextIsDefensivelyCopiedAndNeverRendered() {
        byte[] source = new byte[] {1, 2, 3, 4};
        EncryptedSecret secret = new EncryptedSecret(UUID.randomUUID(), VaultId.generate(),
                SecretId.generate(), SecretVersion.initial(), "AES-256-GCM", 1, "key-2026-01",
                source, Instant.parse("2026-01-01T00:00:00Z"), null);
        source[0] = 99;
        byte[] returned = secret.ciphertext();
        returned[1] = 99;
        assertArrayEquals(new byte[] {1, 2, 3, 4}, secret.ciphertext());
        assertTrue(secret.toString().contains("[REDACTED]"));
    }

    @Test
    void rejectsExpiryThatIsNotAfterCreation() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new EncryptedSecret(
                UUID.randomUUID(), VaultId.generate(), SecretId.generate(), SecretVersion.initial(),
                "AES-256-GCM", 1, "key-1", new byte[] {1}, created, created));
    }

    @Test
    void versionsArePositiveAndMonotonicallyIncremented() {
        assertEquals(2, SecretVersion.initial().next().value());
        assertThrows(IllegalArgumentException.class, () -> new SecretVersion(0));
    }
}
