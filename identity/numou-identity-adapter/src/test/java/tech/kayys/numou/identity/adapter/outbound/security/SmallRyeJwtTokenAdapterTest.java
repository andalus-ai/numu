package tech.kayys.numou.identity.adapter.outbound.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmallRyeJwtTokenAdapterTest {

    @Test
    void acceptsOnlySupportedPrincipalTypes() {
        assertEquals("USER", SmallRyeJwtTokenAdapter.requirePrincipalType("USER"));
        assertEquals("SERVICE", SmallRyeJwtTokenAdapter.requirePrincipalType("SERVICE"));
    }

    @Test
    void rejectsMissingPrincipalTypeInsteadOfDowngradingToUser() {
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType(null));
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType(""));
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType(" "));
    }

    @Test
    void rejectsUnknownOrNonCanonicalPrincipalTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType("ADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType("user"));
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.requirePrincipalType(" USER "));
    }

    @Test
    void rejectsTenantSelectionCredentialsFromBusinessTokenVerification() {
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.rejectTenantSelectionCredential("tenant_selection"));
        SmallRyeJwtTokenAdapter.rejectTenantSelectionCredential("access");
        SmallRyeJwtTokenAdapter.rejectTenantSelectionCredential(null);
    }
    @Test
    void extractsCanonicalTenantClaimAndFallsBackToCompatibilityClaim() {
        UUID canonical = UUID.randomUUID();
        UUID compatibility = UUID.randomUUID();

        assertEquals(canonical, SmallRyeJwtTokenAdapter.parseTenantId(
                canonical.toString(), compatibility.toString()));
        assertEquals(compatibility, SmallRyeJwtTokenAdapter.parseTenantId(
                null, compatibility.toString()));
        assertEquals(null, SmallRyeJwtTokenAdapter.parseTenantId(null, null));
    }

    @Test
    void rejectsMalformedTenantClaim() {
        assertThrows(IllegalArgumentException.class,
                () -> SmallRyeJwtTokenAdapter.parseTenantId("not-a-uuid", null));
    }

}
