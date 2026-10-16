package tech.kayys.syirkah.security.domain.authorization;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecurityContextTest {

    @Test
    void shouldCreateSecurityContextAndCheckPermissions() {
        var pId = PrincipalId.of(UUID.randomUUID());
        var tenantId = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(pId, "carol", tenantId);

        var permRead = Permission.of(ActionType.READ, "product");
        var permWrite = Permission.of(ActionType.CREATE, "product");
        var permDelete = Permission.of(ActionType.DELETE, "product");

        var context = new SecurityContext(principal, tenantId, Set.of(permRead, permWrite));

        assertTrue(context.authenticated());
        assertEquals(principal, context.principal());
        assertEquals(tenantId, context.tenantId());
        assertTrue(context.hasPermission(permRead));
        assertTrue(context.hasPermission(permWrite));
        assertFalse(context.hasPermission(permDelete));
    }
}
