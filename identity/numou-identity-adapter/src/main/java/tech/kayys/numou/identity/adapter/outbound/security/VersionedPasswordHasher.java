package tech.kayys.numou.identity.adapter.outbound.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;
import tech.kayys.numou.identity.application.port.PasswordHasher;
import tech.kayys.numou.identity.domain.valueobject.PasswordHash;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Versioned password hashing with transparent migration from the historical
 * jBCrypt format. New hashes use PBKDF2-HMAC-SHA256 (600,000 iterations,
 * 16-byte random salt, 256-bit output); old BCrypt hashes remain verifiable
 * and are upgraded after a successful login.
 *
 * PBKDF2 is selected as a JCA-backed option suitable for deployments that
 * require a FIPS-validated provider. The provider itself must be validated
 * and configured by the deployment; this class does not claim FIPS status.
 */
@ApplicationScoped
public class VersionedPasswordHasher implements PasswordHasher {

    static final int ITERATIONS = 600_000;
    static final int SALT_BYTES = 16;
    static final int KEY_BITS = 256;
    static final int MAX_PASSWORD_BYTES = 1024;
    private static final String PREFIX = "$numou$pbkdf2-sha256$v=1$i=";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public PasswordHash hash(String rawPassword) {
        validatePassword(rawPassword);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(rawPassword, salt, ITERATIONS, KEY_BITS);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return PasswordHash.of(PREFIX + ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        if (rawPassword == null || hash == null || !withinPasswordLimit(rawPassword)) {
            return false;
        }
        String encoded = hash.value();
        if (encoded.startsWith("$numou$")) {
            return verifyPbkdf2(rawPassword, encoded);
        }
        // Backward compatibility: historical records contain raw jBCrypt strings.
        // Reject inputs beyond BCrypt's 72-byte boundary rather than accepting
        // a password whose suffix BCrypt silently ignores.
        if (!encoded.startsWith("$2a$") && !encoded.startsWith("$2b$") && !encoded.startsWith("$2y$")) {
            return false;
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, encoded);
        } catch (IllegalArgumentException | NullPointerException malformedHash) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(PasswordHash hash) {
        if (hash == null || !hash.value().startsWith(PREFIX)) {
            return true;
        }
        String[] parts = hash.value().split("\\$");
        if (parts.length != 7) {
            return true;
        }
        try {
            return Integer.parseInt(parts[4].substring(2)) != ITERATIONS
                    || Base64.getUrlDecoder().decode(parts[5]).length != SALT_BYTES
                    || Base64.getUrlDecoder().decode(parts[6]).length != KEY_BITS / 8;
        } catch (RuntimeException malformed) {
            return true;
        }
    }

    private boolean verifyPbkdf2(String rawPassword, String encoded) {
        String[] parts = encoded.split("\\$");
        if (parts.length != 7 || !"numou".equals(parts[1])
                || !"pbkdf2-sha256".equals(parts[2]) || !"v=1".equals(parts[3])
                || !parts[4].startsWith("i=")) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[4].substring(2));
            if (iterations < 100_000 || iterations > 2_000_000) {
                return false;
            }
            byte[] salt = Base64.getUrlDecoder().decode(parts[5]);
            byte[] expected = Base64.getUrlDecoder().decode(parts[6]);
            if (salt.length != SALT_BYTES || expected.length != KEY_BITS / 8) {
                return false;
            }
            byte[] actual = derive(rawPassword, salt, iterations, expected.length * 8);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", unavailable);
        } finally {
            spec.clearPassword();
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        if (!withinPasswordLimit(password)) {
            throw new IllegalArgumentException("Password exceeds the maximum UTF-8 length of "
                    + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private static boolean withinPasswordLimit(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
