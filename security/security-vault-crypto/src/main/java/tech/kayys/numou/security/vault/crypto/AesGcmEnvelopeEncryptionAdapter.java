package tech.kayys.numou.security.vault.crypto;

import tech.kayys.numou.security.vault.spi.KeyEncryptionPort;
import tech.kayys.numou.security.vault.spi.SecretEncryptionPort;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * AES-256-GCM envelope encryption. A fresh DEK and 96-bit nonce are generated
 * for every version; the DEK is wrapped by an external KEK provider.
 *
 * The opaque ciphertext frame contains only nonce, wrapped DEK and GCM output.
 * No key material is kept in fields or written to the secret repository.
 */
public final class AesGcmEnvelopeEncryptionAdapter implements SecretEncryptionPort {
    public static final String ALGORITHM = "AES-256-GCM-ENVELOPE";
    public static final int FORMAT_VERSION = 1;
    private static final int DEK_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_WRAPPED_KEY_BYTES = 16 * 1024;
    private static final int MAX_CIPHERTEXT_BYTES = 16 * 1024 * 1024;
    private static final int MAGIC = 0x4E565431; // NVT1

    private final KeyEncryptionPort keys;
    private final SecureRandom random;

    public AesGcmEnvelopeEncryptionAdapter(KeyEncryptionPort keys) {
        this(keys, new SecureRandom());
    }

    AesGcmEnvelopeEncryptionAdapter(KeyEncryptionPort keys, SecureRandom random) {
        this.keys = Objects.requireNonNull(keys, "key encryption port is required");
        this.random = Objects.requireNonNull(random, "secure random is required");
    }

    @Override
    public EncryptedPayload encrypt(EncryptionScope scope, char[] plaintext) {
        Objects.requireNonNull(scope, "scope is required");
        if (plaintext == null || plaintext.length == 0) throw new IllegalArgumentException("plaintext is required");
        byte[] dataKey = new byte[DEK_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] clearBytes = null;
        byte[] wrappedKey = null;
        byte[] wrappingInput = null;
        byte[] encrypted = null;
        try {
            random.nextBytes(dataKey);
            random.nextBytes(nonce);
            String keyId = requireKeyId(keys.activeKeyId(scope.tenantId()));
            clearBytes = encode(plaintext);
            wrappingInput = dataKey.clone();
            wrappedKey = keys.wrap(scope.tenantId(), keyId, wrappingInput);
            if (wrappedKey == null || wrappedKey.length == 0 || wrappedKey.length > MAX_WRAPPED_KEY_BYTES)
                throw new IllegalStateException("KMS returned an invalid wrapped data key");

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(scope, keyId));
            encrypted = cipher.doFinal(clearBytes);
            byte[] frame = frame(nonce, wrappedKey, encrypted);
            return new EncryptedPayload(ALGORITHM, FORMAT_VERSION, keyId, frame);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("vault encryption failed", e);
        } finally {
            wipe(dataKey, nonce, clearBytes, wrappedKey, wrappingInput, encrypted);
        }
    }

    @Override
    public char[] decrypt(EncryptionScope scope, String keyId, String algorithm,
                          int formatVersion, byte[] ciphertext) {
        Objects.requireNonNull(scope, "scope is required");
        if (!ALGORITHM.equals(algorithm) || formatVersion != FORMAT_VERSION)
            throw new IllegalArgumentException("unsupported vault encryption format");
        requireKeyId(keyId);
        if (ciphertext == null || ciphertext.length < 4 + 1 + NONCE_BYTES + 4 + 1 + 4 + 1
                || ciphertext.length > MAX_CIPHERTEXT_BYTES)
            throw new IllegalArgumentException("invalid encrypted envelope");
        byte[] dataKey = null;
        byte[] nonce = null;
        byte[] wrappedKey = null;
        byte[] encrypted = null;
        byte[] clearBytes = null;
        byte[] unwrapInput = null;
        try {
            Envelope envelope = parse(ciphertext);
            nonce = envelope.nonce();
            wrappedKey = envelope.wrappedKey();
            encrypted = envelope.ciphertext();
            unwrapInput = wrappedKey.clone();
            dataKey = keys.unwrap(scope.tenantId(), keyId, unwrapInput);
            if (dataKey == null || dataKey.length != DEK_BYTES)
                throw new GeneralSecurityException("KMS returned an invalid data key");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(scope, keyId));
            clearBytes = cipher.doFinal(encrypted);
            return decode(clearBytes);
        } catch (GeneralSecurityException | IOException e) {
            throw new SecurityException("vault authentication or decryption failed", e);
        } finally {
            wipe(dataKey, nonce, wrappedKey, encrypted, clearBytes, unwrapInput);
        }
    }

    private static byte[] aad(EncryptionScope scope, String keyId) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(FORMAT_VERSION);
            out.writeLong(scope.tenantId().getMostSignificantBits());
            out.writeLong(scope.tenantId().getLeastSignificantBits());
            out.writeLong(scope.vaultId().value().getMostSignificantBits());
            out.writeLong(scope.vaultId().value().getLeastSignificantBits());
            out.writeLong(scope.secretId().value().getMostSignificantBits());
            out.writeLong(scope.secretId().value().getLeastSignificantBits());
            out.writeInt(scope.version());
            out.writeUTF(ALGORITHM);
            out.writeUTF(keyId);
        }
        return bytes.toByteArray();
    }

    private static byte[] frame(byte[] nonce, byte[] wrappedKey, byte[] encrypted) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeByte(FORMAT_VERSION);
            out.writeByte(nonce.length);
            out.write(nonce);
            out.writeInt(wrappedKey.length);
            out.write(wrappedKey);
            out.writeInt(encrypted.length);
            out.write(encrypted);
        }
        return bytes.toByteArray();
    }

    private static Envelope parse(byte[] frame) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != FORMAT_VERSION)
                throw new IOException("unknown envelope frame");
            int nonceLength = in.readUnsignedByte();
            if (nonceLength != NONCE_BYTES) throw new IOException("invalid nonce length");
            byte[] nonce = in.readNBytes(nonceLength);
            int wrappedLength = in.readInt();
            if (wrappedLength < 1 || wrappedLength > MAX_WRAPPED_KEY_BYTES) throw new IOException("invalid wrapped key length");
            byte[] wrapped = in.readNBytes(wrappedLength);
            if (wrapped.length != wrappedLength) throw new EOFException();
            int encryptedLength = in.readInt();
            if (encryptedLength < 16 || encryptedLength > MAX_CIPHERTEXT_BYTES) throw new IOException("invalid ciphertext length");
            byte[] encrypted = in.readNBytes(encryptedLength);
            if (encrypted.length != encryptedLength || in.read() != -1) throw new IOException("truncated or trailing envelope data");
            return new Envelope(nonce, wrapped, encrypted);
        }
    }

    private static byte[] encode(char[] chars) {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(chars));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (buffer.hasArray()) Arrays.fill(buffer.array(), (byte) 0);
            return bytes;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("secret contains invalid characters", e);
        }
    }

    private static char[] decode(byte[] bytes) throws CharacterCodingException {
        CharBuffer buffer = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
        char[] chars = new char[buffer.remaining()];
        buffer.get(chars);
        if (buffer.hasArray()) Arrays.fill(buffer.array(), '\\0');
        return chars;
    }

    private static String requireKeyId(String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 160)
            throw new IllegalStateException("KMS key identifier is invalid");
        return keyId;
    }

    private static void wipe(byte[]... values) {
        for (byte[] value : values) if (value != null) Arrays.fill(value, (byte) 0);
    }

    private record Envelope(byte[] nonce, byte[] wrappedKey, byte[] ciphertext) {}
}
