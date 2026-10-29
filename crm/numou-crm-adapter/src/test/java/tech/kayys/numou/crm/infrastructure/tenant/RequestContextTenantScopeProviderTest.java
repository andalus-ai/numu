package tech.kayys.numou.crm.infrastructure.tenant;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestContextTenantScopeProviderTest {

    @Test
    void rejectsMissingOrUnverifiedTenant() {
        RequestContextHolder context = new RequestContextHolder();
        RequestContextTenantScopeProvider provider = new RequestContextTenantScopeProvider(context);

        assertThrows(SecurityException.class, provider::requireTenantScope);

        context.unverifiedTenant(UUID.randomUUID().toString());
        assertThrows(SecurityException.class, provider::requireTenantScope);
    }

    @Test
    void resolvesOnlyExplicitlyVerifiedTenant() {
        UUID expected = UUID.randomUUID();
        RequestContextHolder context = new RequestContextHolder();
        context.verifiedTenant(expected.toString());

        RequestContextTenantScopeProvider provider = new RequestContextTenantScopeProvider(context);

        assertEquals(expected, provider.requireTenantScope().tenantId().value());
    }

    @Test
    void rejectsMalformedVerifiedTenant() {
        RequestContextHolder context = new RequestContextHolder();
        context.verifiedTenant("not-a-uuid");

        RequestContextTenantScopeProvider provider = new RequestContextTenantScopeProvider(context);

        assertThrows(SecurityException.class, provider::requireTenantScope);
    }
}
