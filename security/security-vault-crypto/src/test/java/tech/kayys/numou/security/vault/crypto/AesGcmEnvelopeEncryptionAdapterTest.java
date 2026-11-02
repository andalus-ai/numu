package tech.kayys.numou.security.vault.crypto;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.security.vault.spi.KeyEncryptionPort;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmEnvelopeEncryptionAdapterTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final SecretEncryptionPort.EncryptionScope SCOPE =
            new SecretEncryptionPort.EncryptionScope(TENANT,
                    new tech.kayys.numou.security.vault.domain.VaultId(UUID.randomUUID()),
                    new tech.kayys.numou.security.vault.domain.SecretId(UUID.randomUUID()), 1);

    @Test
    void roundTripsAndUsesDifferentEnvelopeForEachEncryption() {
        InMemoryKek kek = new InMemoryKek();
        var adapter = new AesGcmEnvelopeEncryptionAdapter(kek);
        char[] input = "sensitive-token-秘密".toCharArray();
        var first = adapter.encrypt(SCOPE, input);
        var second = adapter.encrypt(SCOPE, input);
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
        char[] restored = adapter.decrypt(SCOPE, first.keyId(), first.algorithm(), first.formatVersion(), first.ciphertext());
        assertArrayEquals(input, restored);
        assertFalse(new String(first.ciphertext()).contains("sensitive-token"));
        assertEquals(2, kek.wraps);
        Arrays.fill(input, '\\0');
        Arrays.fill(restored, '\\0');
    }

    @Test
    void rejectsCiphertextTampering() {
        var adapter = new AesGcmEnvelopeEncryptionAdapter(new InMemoryKek());
        var payload = adapter.encrypt(SCOPE, "secret".toCharArray());
        byte[] tampered = payload.ciphertext();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(SecurityException.class, () -> adapter.decrypt(
                SCOPE, payload.keyId(), payload.algorithm(), payload.formatVersion(), tampered));
    }

    @Test
    void rejectsCrossTenantAndCrossVersionReplay() {
        var adapter = new AesGcmEnvelopeEncryptionAdapter(new InMemoryKek());
        var payload = adapter.encrypt(SCOPE, "secret".toCharArray());
        var otherTenant = new SecretEncryptionPort.EncryptionScope(UUID.randomUUID(),
                SCOPE.vaultId(), SCOPE.secretId(), SCOPE.version());
        assertThrows(SecurityException.class, () -> adapter.decrypt(otherTenant,
                payload.keyId(), payload.algorithm(), payload.formatVersion(), payload.ciphertext()));
        var otherVersion = new SecretEncryptionPort.EncryptionScope(TENANT,
                SCOPE.vaultId(), SCOPE.secretId(), 2);
        assertThrows(SecurityException.class, () -> adapter.decrypt(otherVersion,
                payload.keyId(), payload.algorithm(), payload.formatVersion(), payload.ciphertext()));
    }

    @Test
    void rejectsUnknownFormatAndMalformedFrames() {
        var adapter = new AesGcmEnvelopeEncryptionAdapter(new InMemoryKek());
        assertThrows(IllegalArgumentException.class, () -> adapter.decrypt(SCOPE, "kek-v1",
                "AES/CBC/PKCS5Padding", 1, new byte[64]));
        assertThrows(IllegalArgumentException.class, () -> adapter.decrypt(SCOPE, "kek-v1",
                AesGcmEnvelopeEncryptionAdapter.ALGORITHM, 1, new byte[64]));
    }

    private static final class InMemoryKek implements KeyEncryptionPort {
        private final byte[] kek = new byte[32];
        private int wraps;
        private InMemoryKek() { Arrays.fill(kek, (byte) 7); }
        public String activeKeyId(UUID tenantId) { return "test-kek-v1"; }
        public byte[] wrap(UUID tenantId, String keyId, byte[] dataKey) {
            wraps++;
            return crypt(Cipher.ENCRYPT_MODE, dataKey);
        }
        public byte[] unwrap(UUID tenantId, String keyId, byte[] wrappedDataKey) {
            return crypt(Cipher.DECRYPT_MODE, wrappedDataKey);
        }
        private byte[] crypt(int mode, byte[] input) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                byte[] nonce = new byte[12];
                Arrays.fill(nonce, (byte) 3);
                cipher.init(mode, new SecretKeySpec(kek, "AES"), new GCMParameterSpec(128, nonce));
                return cipher.doFinal(input);
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
    }
}
