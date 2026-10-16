package tech.kayys.syirkah.security.domain.authorization;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.identifier.PrincipalId;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccessRequestTest {

    @Test
    void shouldCreateAccessRequestWithTenantId() {
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "alice", UUID.randomUUID());
        var tenantId = TenantId.of(UUID.randomUUID());

        var request = AccessRequest.of(principal, tenantId, ActionType.READ, "order");

        assertEquals(principal, request.principal());
        assertEquals(tenantId, request.tenantId());
        assertEquals(ActionType.READ, request.action());
        assertEquals("order", request.resourceType());
        assertEquals("", request.resourceId());
        assertEquals("", request.context());
    }

    @Test
    void shouldCreateAccessRequestWithUUIDBackwardsCompatibility() {
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", UUID.randomUUID());
        var uuid = UUID.randomUUID();

        var request = AccessRequest.of(principal, uuid, ActionType.UPDATE, "invoice");

        assertEquals(TenantId.of(uuid), request.tenantId());
        assertEquals(ActionType.UPDATE, request.action());
        assertEquals("invoice", request.resourceType());
    }

    @Test
    void shouldRejectNullPrincipalOrTenant() {
        var tenantId = TenantId.of(UUID.randomUUID());
        assertThrows(NullPointerException.class, () ->
                AccessRequest.of(null, tenantId, ActionType.READ, "order")
        );
        var principal = Principal.of(PrincipalId.of(UUID.randomUUID()), "bob", UUID.randomUUID());
        assertThrows(NullPointerException.class, () ->
                AccessRequest.of(principal, (TenantId) null, ActionType.READ, "order")
        );
    }
}
