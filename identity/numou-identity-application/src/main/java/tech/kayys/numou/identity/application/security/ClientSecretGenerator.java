package tech.kayys.numou.identity.application.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Creates high-entropy OAuth client secrets. Persist only the returned SHA-256
 * digest and display prefix; disclose {@link SecretMaterial#takeSecret()} once.
 */
public final class ClientSecretGenerator {
    private static final String PREFIX = "nmc_live_";
    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    public SecretMaterial generate() {
        byte[] entropy = new byte[SECRET_BYTES];
        RANDOM.nextBytes(entropy);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        Arrays.fill(entropy, (byte) 0);
        return new SecretMaterial(secret, sha256(secret), secret.substring(0, 16));
    }

    /** Constant-time digest comparison; supplied secret is never logged or retained. */
    public boolean matches(String suppliedSecret, String expectedHash) {
        if (suppliedSecret == null || expectedHash == null || !expectedHash.matches("[a-fA-F0-9]{64}")) {
            return false;
        }
        byte[] actual = digest(suppliedSecret);
        byte[] expected;
        try {
            expected = hexToBytes(expectedHash);
        } catch (IllegalArgumentException invalidHash) {
            Arrays.fill(actual, (byte) 0);
            return false;
        }
        try {
            return MessageDigest.isEqual(actual, expected);
        } finally {
            Arrays.fill(actual, (byte) 0);
            Arrays.fill(expected, (byte) 0);
        }
    }

    private static String sha256(String value) {
        return toHex(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid digest");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        Arrays.fill(bytes, (byte) 0);
        return result.toString();
    }

    public static final class SecretMaterial {
        private String secret;
        private final String secretHash;
        private final String displayPrefix;

        private SecretMaterial(String secret, String secretHash, String displayPrefix) {
            this.secret = Objects.requireNonNull(secret);
            this.secretHash = Objects.requireNonNull(secretHash);
            this.displayPrefix = Objects.requireNonNull(displayPrefix);
        }

        /** Returns the raw secret once; subsequent attempts fail closed. */
        public synchronized String takeSecret() {
            if (secret == null) throw new IllegalStateException("client secret has already been disclosed");
            String disclosed = secret;
            secret = null;
            return disclosed;
        }
        public String secretHash() { return secretHash; }
        public String displayPrefix() { return displayPrefix; }

        @Override
        public String toString() {
            return "SecretMaterial[secret=<redacted>, secretHash=<redacted>, displayPrefix="
                    + displayPrefix + "]";
        }
    }
}
