package tech.kayys.syirkah.identity.domain.role;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    @Test
    void permissions_are_scoped_to_tenant_role_and_grants_are_idempotent() {
        var role = Role.create(
                RoleId.generate(), TenantId.generate(), "finance.approver", "Approver", Instant.now()
        );
        var permission = Permission.of("purchase-order.approve");

        role.grant(permission, Instant.now());
        role.grant(permission, Instant.now());

        assertTrue(role.hasPermission(permission));
        assertEquals(1, role.permissions().size());
        assertEquals(2, role.pullDomainEvents().size());
    }

    @Test
    void built_in_role_cannot_be_renamed_or_have_permissions_revoked() {
        var role = Role.defaultRole(RoleId.generate(), TenantId.generate(), "owner", "Owner", Instant.now());

        assertThrows(IllegalStateException.class, () -> role.rename("Changed"));
        assertThrows(
                IllegalStateException.class,
                () -> role.revoke(Permission.of("document.read"), Instant.now())
        );
    }

    @Test
    void permission_names_must_be_qualified_and_lowercase() {
        assertThrows(IllegalArgumentException.class, () -> Permission.of("Document.Read"));
        assertThrows(IllegalArgumentException.class, () -> Permission.of("document"));
        assertEquals("document.read", Permission.of("document.read").value());
    }
}
