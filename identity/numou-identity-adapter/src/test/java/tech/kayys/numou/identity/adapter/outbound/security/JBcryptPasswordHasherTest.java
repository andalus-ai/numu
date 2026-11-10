package tech.kayys.numou.identity.adapter.outbound.security;

import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import tech.kayys.numou.identity.domain.valueobject.PasswordHash;

import static org.junit.jupiter.api.Assertions.*;

class JBcryptPasswordHasherTest {

    private final JBcryptPasswordHasher hasher = new JBcryptPasswordHasher();

    @Test
    void hashesWithVersionedPbkdf2AndVerifiesCorrectPassword() {
        PasswordHash hash = hasher.hash("correct horse battery staple");

        assertTrue(hash.value().startsWith("$numou$pbkdf2-sha256$v=1$i=600000$"));
        assertTrue(hasher.matches("correct horse battery staple", hash));
        assertFalse(hasher.matches("incorrect password", hash));
        assertFalse(hasher.needsRehash(hash));
    }

    @Test
    void generatesDifferentHashesForSamePassword() {
        PasswordHash first = hasher.hash("same-password");
        PasswordHash second = hasher.hash("same-password");

        assertNotEquals(first.value(), second.value());
        assertTrue(hasher.matches("same-password", first));
        assertTrue(hasher.matches("same-password", second));
    }

    @Test
    void verifiesUnicodePasswordsWithoutNormalization() {
        String password = "pässwörd-日本語-🔐";
        PasswordHash hash = hasher.hash(password);

        assertTrue(hasher.matches(password, hash));
        assertFalse(hasher.matches("passwörd-日本語-🔐", hash));
    }

    @Test
    void verifiesLegacyBcryptAndRequestsUpgrade() {
        String legacy = BCrypt.hashpw("legacy-password", BCrypt.gensalt(4));
        PasswordHash hash = PasswordHash.of(legacy);

        assertTrue(hasher.matches("legacy-password", hash));
        assertFalse(hasher.matches("wrong-password", hash));
        assertTrue(hasher.needsRehash(hash));
    }

    @Test
    void rejectsMalformedEncodedHashesWithoutThrowing() {
        PasswordHash malformed = PasswordHash.of("$numou$pbkdf2-sha256$v=1$i=not-a-number$bad$bad");

        assertFalse(hasher.matches("password", malformed));
        assertTrue(hasher.needsRehash(malformed));
        assertFalse(hasher.matches("password", PasswordHash.of("not-a-supported-hash")));
    }

    @Test
    void rejectsPasswordsAboveConfiguredUtf8ByteLimit() {
        String tooLong = "界".repeat(400); // 1,200 UTF-8 bytes

        assertThrows(IllegalArgumentException.class, () -> hasher.hash(tooLong));
        assertFalse(hasher.matches(tooLong, PasswordHash.of("$2a$04$invalid")));
    }

    @Test
    void doesNotVerifyLegacyBcryptInputsBeyondIts72ByteBoundary() {
        String legacy = BCrypt.hashpw("a".repeat(72), BCrypt.gensalt(4));

        assertFalse(hasher.matches("a".repeat(72) + "suffix", PasswordHash.of(legacy)));
    }
}
