package tech.kayys.numou.notification.provider.http;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;

/** RFC 8291 aes128gcm single-record payload encryption. */
final class WebPushPayloadEncryptor {
    private static final byte[] KEY_INFO = "WebPush: info".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    byte[] encrypt(WebPushSubscription subscription, byte[] plaintext) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair sender = generator.generateKeyPair();
        byte[] senderPublic = encode((ECPublicKey) sender.getPublic());
        PublicKey receiver = decode(subscription.p256dh());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(receiver, true);
        byte[] sharedSecret = agreement.generateSecret();
        byte[] keyPrk = extract(subscription.auth(), sharedSecret);
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.write(KEY_INFO);
        info.write(0);
        info.write(subscription.p256dh());
        info.write(senderPublic);
        byte[] ikm = expand(keyPrk, info.toByteArray(), 32);

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] prk = extract(salt, ikm);
        byte[] cek = expand(prk, CEK_INFO, 16);
        byte[] nonce = expand(prk, NONCE_INFO, 12);
        byte[] record = Arrays.copyOf(plaintext, plaintext.length + 1);
        record[plaintext.length] = 2;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(record);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(salt);
        output.write(ByteBuffer.allocate(4).putInt(4096).array());
        output.write(senderPublic.length);
        output.write(senderPublic);
        output.write(ciphertext);
        return output.toByteArray();
    }

    private static byte[] extract(byte[] salt, byte[] input) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(input);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] result = new byte[length], previous = new byte[0];
        int offset = 0;
        for (int counter = 1; offset < length; counter++) {
            mac.reset();
            mac.update(previous);
            mac.update(info);
            mac.update((byte) counter);
            previous = mac.doFinal();
            int count = Math.min(previous.length, length - offset);
            System.arraycopy(previous, 0, result, offset, count);
            offset += count;
        }
        return result;
    }

    private static PublicKey decode(byte[] encoded) throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] xBytes = Arrays.copyOfRange(encoded, 1, 33);
        byte[] yBytes = Arrays.copyOfRange(encoded, 33, 65);
        ECPoint point = new ECPoint(new java.math.BigInteger(1, xBytes), new java.math.BigInteger(1, yBytes));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
    }

    private static byte[] encode(ECPublicKey key) {
        byte[] result = new byte[65];
        result[0] = 4;
        put32(key.getW().getAffineX(), result, 1);
        put32(key.getW().getAffineY(), result, 33);
        return result;
    }

    private static void put32(java.math.BigInteger value, byte[] target, int offset) {
        byte[] raw = value.toByteArray();
        int start = raw.length > 32 ? raw.length - 32 : 0;
        int length = raw.length - start;
        System.arraycopy(raw, start, target, offset + 32 - length, length);
    }
}
