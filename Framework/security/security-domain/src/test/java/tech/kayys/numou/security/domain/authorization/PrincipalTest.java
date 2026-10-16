package tech.kayys.syirkah.security.domain.authorization;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrincipalTest {

    @Test
    void shouldCreatePrincipalWithTenantIds() {
        var pId = PrincipalId.of(UUID.randomUUID());
        var t1 = TenantId.of(UUID.randomUUID());
        var t2 = TenantId.of(UUID.randomUUID());

        var principal = new Principal(pId, "john.doe", Set.of(t1, t2));

        assertEquals("john.doe", principal.handle());
        assertEquals(2, principal.tenantIds().size());
        assertTrue(principal.isMemberOf(t1));
        assertTrue(principal.isMemberOf(t2));
        assertFalse(principal.isMemberOf(TenantId.of(UUID.randomUUID())));
        assertFalse(principal.isPlatformPrincipal());
    }

    @Test
    void shouldSupportUUIDBackwardsCompatibility() {
        var pId = PrincipalId.of(UUID.randomUUID());
        var uuid = UUID.randomUUID();

        var principal = Principal.of(pId, "admin", uuid);

        assertTrue(principal.isMemberOf(uuid));
        assertTrue(principal.isMemberOf(TenantId.of(uuid)));
        assertFalse(principal.isPlatformPrincipal());
    }

    @Test
    void shouldRecognizePlatformPrincipalWhenNoTenantsAssigned() {
        var pId = PrincipalId.of(UUID.randomUUID());
        var principal = new Principal(pId, "system", Set.of());

        assertTrue(principal.isPlatformPrincipal());
        assertFalse(principal.isMemberOf(TenantId.of(UUID.randomUUID())));
    }
}
