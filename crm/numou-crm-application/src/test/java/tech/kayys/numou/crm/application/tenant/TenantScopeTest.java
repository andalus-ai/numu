package tech.kayys.numou.crm.application.tenant;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantScopeTest {

    @Test
    void scopeRequiresTenantId() {
        assertThrows(NullPointerException.class, () -> new TenantScope(null));
    }

    @Test
    void scopeRetainsTenantIdWithoutReinterpretingIt() {
        TenantId tenantId = TenantId.of(UUID.fromString("b80a1d1b-3e18-4b79-9a7a-6b2ed62a9f4d"));

        TenantScope scope = new TenantScope(tenantId);

        assertEquals(tenantId, scope.tenantId());
    }
}
