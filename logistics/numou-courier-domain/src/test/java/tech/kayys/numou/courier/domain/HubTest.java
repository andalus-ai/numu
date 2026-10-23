package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationUnitId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HubTest {
    private static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

    private Hub newHub() {
        return Hub.create(HubId.generate(), TenantId.generate(), OrganizationId.generate(),
                OrganizationUnitId.generate(), "bdg-01", "Bandung Main Hub",
                HubType.ORIGIN_HUB,
                new HubAddress("Jl. Example 1", null, "Bandung", "West Java", "40111", "id",
                        -6.9175, 107.6191),
                ZoneId.of("Asia/Jakarta"), NOW);
    }

    @Test
    void normalizesCodeAndEnforcesLifecycle() {
        Hub hub = newHub();
        assertThat(hub.code()).isEqualTo("BDG-01");
        assertThat(hub.status()).isEqualTo(HubStatus.DRAFT);

        hub.activate(NOW.plusSeconds(1));
        hub.suspend(NOW.plusSeconds(2));
        hub.resume(NOW.plusSeconds(3));
        hub.close(NOW.plusSeconds(4));

        assertThat(hub.status()).isEqualTo(HubStatus.CLOSED);
        assertThatThrownBy(() -> hub.resume(NOW.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidCoordinatesAndPartialCoordinates() {
        assertThatThrownBy(() -> new HubAddress("Street", null, "Bandung", null, null, "ID",
                91.0, 107.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HubAddress("Street", null, "Bandung", null, null, "ID",
                -6.9, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessScopeRejectsCrossTenantAndOutOfScopeHub() {
        Hub hub = newHub();
        HubAccessScope otherTenant = new HubAccessScope(TenantId.generate(), Set.of(), Set.of(), true);
        assertThatThrownBy(() -> otherTenant.requireCanAccess(hub))
                .isInstanceOf(SecurityException.class);

        HubAccessScope sameTenant = new HubAccessScope(hub.tenantId(), Set.of(), Set.of(), false);
        assertThatThrownBy(() -> sameTenant.requireCanAccess(hub))
                .isInstanceOf(SecurityException.class);

        HubAccessScope branchScope = new HubAccessScope(hub.tenantId(),
                Set.of(hub.branchUnitId()), Set.of(), false);
        assertThat(branchScope).satisfies(scope -> scope.requireCanAccess(hub));
    }
}
