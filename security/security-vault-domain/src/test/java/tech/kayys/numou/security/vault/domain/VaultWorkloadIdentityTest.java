package tech.kayys.numou.security.vault.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultWorkloadIdentityTest {
    @Test
    void identityIsTenantBoundAndExpiresAtTheDeclaredInstant() {
        UUID tenant = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-11-02T10:00:00Z");
        VaultWorkloadIdentity identity = new VaultWorkloadIdentity(tenant, "https://issuer.example",
                "system:payments", Set.of("numou-vault"), Set.of("secret:read"), expiry);

        assertTrue(identity.isValidFor(tenant, expiry.minusNanos(1)));
        assertFalse(identity.isValidFor(UUID.randomUUID(), expiry.minusNanos(1)));
        assertFalse(identity.isValidFor(tenant, expiry));
    }

    @Test
    void rejectsUnboundedOrEmptyClaims() {
        UUID tenant = UUID.randomUUID();
        Instant expiry = Instant.parse("2026-11-02T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VaultWorkloadIdentity(
                tenant, "issuer", "subject", Set.of(), Set.of("secret:read"), expiry));
        assertThrows(IllegalArgumentException.class, () -> new VaultWorkloadIdentity(
                tenant, "issuer", "subject", Set.of("vault"), Set.of(" "), expiry));
    }
}
