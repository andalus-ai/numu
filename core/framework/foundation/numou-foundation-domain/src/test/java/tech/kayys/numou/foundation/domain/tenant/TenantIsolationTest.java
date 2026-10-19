package tech.kayys.numou.foundation.domain.tenant;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.repository.TenantAware;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantIsolationTest {

    record DummyTenantAggregate(TenantId tenantId, String data) implements TenantAware {}

    @Test
    void shouldPassWhenTenantMatches() {
        var tenant = TenantId.of(UUID.randomUUID());
        var aggregate = new DummyTenantAggregate(tenant, "test");

        assertDoesNotThrow(() -> TenantIsolation.verify(tenant, aggregate));
    }

    @Test
    void shouldThrowViolationWhenTenantDiffers() {
        var tenantA = TenantId.of(UUID.randomUUID());
        var tenantB = TenantId.of(UUID.randomUUID());
        var aggregate = new DummyTenantAggregate(tenantB, "test");

        var ex = assertThrows(TenantIsolationViolation.class, () ->
                TenantIsolation.verify(tenantA, aggregate)
        );

        assertEquals(tenantA, ex.currentTenant());
        assertEquals(tenantB, ex.aggregateTenant());
    }

    @Test
    void shouldThrowWhenCurrentTenantIsNull() {
        var tenantB = TenantId.of(UUID.randomUUID());
        var aggregate = new DummyTenantAggregate(tenantB, "test");

        assertThrows(IllegalStateException.class, () ->
                TenantIsolation.verify(null, aggregate)
        );
    }

    @Test
    void shouldThrowWhenAggregateIsNull() {
        var tenantA = TenantId.of(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () ->
                TenantIsolation.verify(tenantA, null)
        );
    }
}
