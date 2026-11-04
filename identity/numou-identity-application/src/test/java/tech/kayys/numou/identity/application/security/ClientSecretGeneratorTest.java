package tech.kayys.numou.identity.application.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientSecretGeneratorTest {
    private final ClientSecretGenerator generator = new ClientSecretGenerator();

    @Test
    void generatesUniqueHighEntropySecretsAndStableDigests() {
        var first = generator.generate();
        var second = generator.generate();

        assertNotEquals(first.secret(), second.secret());
        assertTrue(first.secret().startsWith("nmc_live_"));
        assertEquals(64, first.secretHash().length());
        assertTrue(generator.matches(first.secret(), first.secretHash()));
        assertFalse(generator.matches(second.secret(), first.secretHash()));
        assertEquals(first.secret().substring(0, 16), first.displayPrefix());
        assertFalse(first.toString().contains(first.secret()));
        assertFalse(first.toString().contains(first.secretHash()));
    }

    @Test
    void rejectsNullAndMalformedHashesWithoutThrowing() {
        assertFalse(generator.matches(null, "a".repeat(64)));
        assertFalse(generator.matches("secret", null));
        assertFalse(generator.matches("secret", "not-a-hash"));
    }
}
