package tech.kayys.syirkah.identity.adapter.outbound.security;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.EffectivePermissionPort;
import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.security.domain.authorization.Principal;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdentityPolicyAdapterTest {

    @Test
    void shouldMapPermissionUsingLastDot() {
        var p1 = IdentityPermissionMapper.toSecurityPermission("product.read");
        assertEquals("product", p1.resource());
        assertEquals("read", p1.action());
        assertEquals("product.read", p1.name());

        var p2 = IdentityPermissionMapper.toSecurityPermission("sales.order.read");
        assertEquals("sales.order", p2.resource());
        assertEquals("read", p2.action());
        assertEquals("sales.order.read", p2.name());

        var domainPerm = new tech.kayys.syirkah.identity.domain.role.Permission("financial.ledger.post");
        var p3 = IdentityPermissionMapper.toSecurityPermission(domainPerm);
        assertEquals("financial.ledger", p3.resource());
        assertEquals("post", p3.action());
    }

    @Test
    void shouldRejectMalformedPermissionCodes() {
        assertThrows(IllegalArgumentException.class, () -> IdentityPermissionMapper.toSecurityPermission(""));
        assertThrows(IllegalArgumentException.class, () -> IdentityPermissionMapper.toSecurityPermission("nodot"));
        assertThrows(IllegalArgumentException.class, () -> IdentityPermissionMapper.toSecurityPermission(".leadingdot"));
        assertThrows(IllegalArgumentException.class, () -> IdentityPermissionMapper.toSecurityPermission("trailingdot."));
    }

    @Test
    void shouldReturnEmptyPermissionsWhenPrincipalNotMemberOfTenant() {
        var userUuid = UUID.randomUUID();
        var tenantA = TenantId.of(UUID.randomUUID());
        var tenantB = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(userUuid), "alice", tenantA);

        EffectivePermissionPort effectivePort = (t, u) -> Uni.createFrom().item(Set.of("product.read", "product.create"));
        var adapter = new IdentityPolicyAdapter(effectivePort);

        var permissionsInB = adapter.permissionsFor(principal, tenantB).await().indefinitely();
        assertTrue(permissionsInB.isEmpty());
    }

    @Test
    void shouldResolveEffectivePermissionsWhenPrincipalIsMember() {
        var userUuid = UUID.randomUUID();
        var tenantA = TenantId.of(UUID.randomUUID());
        var principal = Principal.of(PrincipalId.of(userUuid), "alice", tenantA);

        EffectivePermissionPort effectivePort = (tenantId, userId) -> {
            if (tenantId.equals(tenantA) && userId.equals(UserId.of(userUuid))) {
                return Uni.createFrom().item(Set.of("product.read", "product.create"));
            }
            return Uni.createFrom().item(Set.of());
        };

        var adapter = new IdentityPolicyAdapter(effectivePort);
        var permissionsInA = adapter.permissionsFor(principal, tenantA).await().indefinitely();

        assertEquals(2, permissionsInA.size());
        assertTrue(permissionsInA.stream().anyMatch(p -> p.name().equals("product.read")));
        assertTrue(permissionsInA.stream().anyMatch(p -> p.name().equals("product.create")));
    }
}
