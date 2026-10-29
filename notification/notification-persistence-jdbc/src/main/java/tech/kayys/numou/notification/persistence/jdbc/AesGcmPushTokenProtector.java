package tech.kayys.numou.notification.persistence.jdbc;

import tech.kayys.numou.notification.application.port.PushTokenProtector;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** AES-256/128-GCM token protection with per-value nonce and owner-bound AAD. */
public final class AesGcmPushTokenProtector implements PushTokenProtector {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PushTokenKeyProvider keys;

    public AesGcmPushTokenProtector(PushTokenKeyProvider keys) {
        this.keys = Objects.requireNonNull(keys, "keys is required");
    }

    @Override
    public EncryptedToken protect(UUID tenantId, UUID recipientId, String plaintextToken) {
        requireScope(tenantId, recipientId);
        if (plaintextToken == null || plaintextToken.isBlank())
            throw new IllegalArgumentException("provider token is required");
        PushTokenKeyProvider.KeyMaterial active = Objects.requireNonNull(keys.activeKey(), "active key is required");
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, active.key(), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId, recipientId));
            byte[] encrypted = cipher.doFinal(plaintextToken.getBytes(StandardCharsets.UTF_8));
            ByteBuffer envelope = ByteBuffer.allocate(nonce.length + encrypted.length);
            envelope.put(nonce).put(encrypted);
            return new EncryptedToken(active.keyId(), Base64.getEncoder().encodeToString(envelope.array()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect push token", exception);
        }
    }

    @Override
    public String reveal(UUID tenantId, UUID recipientId, String keyId, String ciphertext) {
        requireScope(tenantId, recipientId, deviceId);
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
        final byte[] envelope;
        try {
            envelope = Base64.getDecoder().decode(Objects.requireNonNull(ciphertext, "ciphertext is required"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored push token envelope is malformed");
        }
        if (envelope.length <= NONCE_BYTES + 16) throw new IllegalStateException("Stored push token envelope is malformed");
        SecretKey key = keys.keyFor(keyId).orElseThrow(() -> new IllegalStateException("Push token decryption key is unavailable"));
        byte[] nonce = java.util.Arrays.copyOfRange(envelope, 0, NONCE_BYTES);
        byte[] encrypted = java.util.Arrays.copyOfRange(envelope, NONCE_BYTES, envelope.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(tenantId, recipientId, deviceId));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt push token", exception);
        }
    }

    private static byte[] aad(UUID tenantId, UUID recipientId) {
        return ("numou:notification:push-token:v1:" + tenantId + ":" + recipientId)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void requireScope(UUID tenantId, UUID recipientId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(recipientId, "recipientId is required");
    }
}
