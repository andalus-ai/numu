package tech.kayys.numou.identity.application.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientSecretGeneratorTest {
    private final ClientSecretGenerator generator = new ClientSecretGenerator();

    @Test
    void generatesUniqueHighEntropySecretsAndDisclosesEachOnlyOnce() {
        var first = generator.generate();
        var second = generator.generate();
        String firstSecret = first.takeSecret();
        String secondSecret = second.takeSecret();

        assertNotEquals(firstSecret, secondSecret);
        assertTrue(firstSecret.startsWith("nmc_live_"));
        assertEquals(64, first.secretHash().length());
        assertTrue(generator.matches(firstSecret, first.secretHash()));
        assertFalse(generator.matches(secondSecret, first.secretHash()));
        assertEquals(firstSecret.substring(0, 16), first.displayPrefix());
        assertFalse(first.toString().contains(firstSecret));
        assertFalse(first.toString().contains(first.secretHash()));
        assertThrows(IllegalStateException.class, first::takeSecret);
    }

    @Test
    void rejectsNullAndMalformedHashesWithoutThrowing() {
        assertFalse(generator.matches(null, "a".repeat(64)));
        assertFalse(generator.matches("secret", null));
        assertFalse(generator.matches("secret", "not-a-hash"));
    }
}
