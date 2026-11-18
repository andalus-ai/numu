package tech.kayys.numou.automotiveparts.purchasing;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptPostingTenantContextFilterTest {
    @Test
    void acceptsOnlyTenantBoundHumanAccessTokens() {
        String tenant = UUID.randomUUID().toString();

        assertEquals(tenant, ReceiptPostingTenantContextFilter.resolveVerifiedTenant(
                "USER", "access", tenant, tenant));
        assertEquals(tenant, ReceiptPostingTenantContextFilter.resolveVerifiedTenant(
                "USER", "access", null, tenant));
    }

    @Test
    void rejectsSelectionTokensServiceTokensAndTenantlessLegacyTokens() {
        String tenant = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.resolveVerifiedTenant("USER", "tenant_selection", tenant, null));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.resolveVerifiedTenant("SERVICE", "access", tenant, null));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.resolveVerifiedTenant("USER", "access", null, null));
    }

    @Test
    void rejectsConflictingOrNonCanonicalTenantClaims() {
        String tenant = UUID.randomUUID().toString();

        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.resolveVerifiedTenant(
                        "USER", "access", tenant, UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.resolveVerifiedTenant("USER", "access", "1-1-1-1-1", null));
    }

    @Test
    void requiresExactIdentityIssuerAndReceiptApiAudience() {
        Set<String> audiences = Set.of("numou-api", "another-service");

        assertDoesNotThrow(() -> ReceiptPostingTenantContextFilter.requireIssuerAndAudience(
                "https://identity.numou.internal", audiences,
                "https://identity.numou.internal", "numou-api"));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.requireIssuerAndAudience(
                        "https://attacker.example", audiences,
                        "https://identity.numou.internal", "numou-api"));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.requireIssuerAndAudience(
                        "https://identity.numou.internal", audiences,
                        "https://identity.numou.internal", "automotive-parts-api"));
        assertThrows(IllegalArgumentException.class, () ->
                ReceiptPostingTenantContextFilter.requireIssuerAndAudience(
                        "https://identity.numou.internal", Set.of(),
                        "https://identity.numou.internal", "numou-api"));
    }
}
