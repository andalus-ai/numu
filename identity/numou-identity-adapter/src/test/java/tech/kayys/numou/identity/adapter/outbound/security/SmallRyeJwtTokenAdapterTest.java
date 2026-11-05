package tech.kayys.numou.identity.adapter.outbound.security;

import org.junit.jupiter.api.Test;

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
}
