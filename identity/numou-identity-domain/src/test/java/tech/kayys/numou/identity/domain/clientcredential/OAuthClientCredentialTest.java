package tech.kayys.numou.identity.domain.clientcredential;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OAuthClientCredentialTest {
    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    private static final String HASH_1 = "a".repeat(64);
    private static final String HASH_2 = "b".repeat(64);

    private OAuthClientCredential issue() {
        return OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), UUID.randomUUID(), UUID.randomUUID(),
                "client_test", HASH_1, "nmc_live_abc", Set.of("orders.read", "orders.write"),
                NOW.plusSeconds(3600), NOW);
    }

    @Test
    void issuesTenantAndApplicationBoundCredentialWithImmutableScopes() {
        var credential = issue();
        assertNotNull(credential.tenantId());
        assertNotNull(credential.applicationId());
        assertEquals(Set.of("orders.read", "orders.write"), credential.scopes());
        assertEquals(1, credential.credentialVersion());
        assertTrue(credential.isUsableAt(NOW.plusSeconds(1)));
        assertFalse(credential.isUsableAt(NOW.plusSeconds(3600)));
    }

    @Test
    void rejectsMissingTenantApplicationAndEmptyScopes() {
        assertThrows(NullPointerException.class, () -> OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), null, UUID.randomUUID(), "client_test",
                HASH_1, "nmc_live_abc", Set.of("orders.read"), null, NOW));
        assertThrows(NullPointerException.class, () -> OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), UUID.randomUUID(), null, "client_test",
                HASH_1, "nmc_live_abc", Set.of("orders.read"), null, NOW));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), UUID.randomUUID(), UUID.randomUUID(), "client_test",
                HASH_1, "nmc_live_abc", Set.of(), null, NOW));
    }

    @Test
    void rejectsMalformedSecretHashAndScope() {
        assertThrows(IllegalArgumentException.class, () -> OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), UUID.randomUUID(), UUID.randomUUID(), "client_test",
                "plaintext-secret", "nmc_live_abc", Set.of("orders.read"), null, NOW));
        assertThrows(IllegalArgumentException.class, () -> OAuthClientCredential.issue(
                OAuthClientCredentialId.newId(), UUID.randomUUID(), UUID.randomUUID(), "client_test",
                HASH_1, "nmc_live_abc", Set.of("../admin"), null, NOW));
    }

    @Test
    void rotatesSecretHashAndIncrementsCredentialVersion() {
        var credential = issue();
        credential.rotate(HASH_2, "nmc_live_def", NOW.plusSeconds(7200), NOW.plusSeconds(10));
        assertEquals(HASH_2, credential.secretHash());
        assertEquals("nmc_live_def", credential.secretPrefix());
        assertEquals(2, credential.credentialVersion());
        assertEquals(NOW.plusSeconds(10), credential.rotatedAt());
    }

    @Test
    void rejectsRotationAfterExpiryAndRevocationIsTerminal() {
        var credential = issue();
        assertThrows(IllegalStateException.class,
                () -> credential.rotate(HASH_2, "nmc_live_def", null, NOW.plusSeconds(3601)));
        credential.revoke(NOW.plusSeconds(20));
        assertEquals(OAuthClientCredentialStatus.REVOKED, credential.status());
        assertFalse(credential.isUsableAt(NOW.plusSeconds(21)));
        assertThrows(IllegalStateException.class,
                () -> credential.rotate(HASH_2, "nmc_live_def", null, NOW.plusSeconds(30)));
    }

    @Test
    void revokeIsIdempotent() {
        var credential = issue();
        credential.revoke(NOW.plusSeconds(10));
        credential.revoke(NOW.plusSeconds(20));
        assertEquals(NOW.plusSeconds(10), credential.revokedAt());
    }
}
