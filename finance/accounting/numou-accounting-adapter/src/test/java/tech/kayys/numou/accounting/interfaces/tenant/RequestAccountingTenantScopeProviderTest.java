package tech.kayys.numou.accounting.interfaces.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.adapter.context.TenantContext;

import static org.junit.jupiter.api.Assertions.*;

class RequestAccountingTenantScopeProviderTest {

    private final RequestAccountingTenantScopeProvider provider =
            new RequestAccountingTenantScopeProvider();

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void resolvesExplicitRequestTenant() {
        TenantContext.setTenantId("tenant-acme");

        assertEquals("tenant-acme", provider.currentTenant().value());
    }

    @Test
    void rejectsMissingTenantInsteadOfFallingBackToDefaultTenant() {
        TenantContext.clear();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, provider::currentTenant);

        assertTrue(failure.getMessage().contains("authenticated tenant context"));
    }

    @Test
    void rejectsBlankTenant() {
        TenantContext.setTenantId("   ");

        assertThrows(IllegalStateException.class, provider::currentTenant);
    }
}
