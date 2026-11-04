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
    void operationsExposeStableLeastPrivilegeScopeNames() {
        assertEquals("secret:create", VaultOperation.CREATE.requiredScope());
        assertEquals("secret:read", VaultOperation.READ.requiredScope());
        assertEquals("secret:rotate", VaultOperation.ROTATE.requiredScope());
        assertEquals("secret:revoke", VaultOperation.REVOKE.requiredScope());
    }

    @Test
    void permitsOnlyTrustedIssuerAudienceTenantAndOperationScope() {
        UUID tenant = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T00:00:00Z");
        VaultWorkloadIdentity identity = new VaultWorkloadIdentity(tenant, "spiffe://prod.example",
                "spiffe://prod.example/ns/payments/sa/worker",
                Set.of("numou-vault", "payments-api"), Set.of("secret:read"), now.plusSeconds(60));

        assertTrue(identity.permits(tenant, "spiffe://prod.example", "numou-vault",
                VaultOperation.READ, now));
        assertFalse(identity.permits(tenant, "spiffe://untrusted.example", "numou-vault",
                VaultOperation.READ, now));
        assertFalse(identity.permits(tenant, "spiffe://prod.example", "other-service",
                VaultOperation.READ, now));
        assertFalse(identity.permits(UUID.randomUUID(), "spiffe://prod.example", "numou-vault",
                VaultOperation.READ, now));
        assertFalse(identity.permits(tenant, "spiffe://prod.example", "numou-vault",
                VaultOperation.ROTATE, now));
        assertFalse(identity.permits(tenant, "spiffe://prod.example", "numou-vault",
                VaultOperation.READ, now.plusSeconds(60)));
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
