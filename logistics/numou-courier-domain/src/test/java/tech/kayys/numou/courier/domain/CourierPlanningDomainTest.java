package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CourierPlanningDomainTest {
    private final Instant now = Instant.parse("2026-10-24T02:00:00Z");

    @Test void etaSnapshotsValidateConfidenceAndWindow() {
        TenantId tenant = TenantId.generate();
        ShipmentId shipment = ShipmentId.generate();
        assertThrows(IllegalArgumentException.class, () -> new CourierEtaEstimate(UUID.randomUUID(), tenant, shipment,
            now.plusSeconds(3600), now, now, new BigDecimal("0.7"), "rules-v1",
            CourierEtaEstimate.Source.RULES, "TRANSIT_BASELINE"));
        assertThrows(IllegalArgumentException.class, () -> new CourierEtaEstimate(UUID.randomUUID(), tenant, shipment,
            now, now.plusSeconds(3600), now, new BigDecimal("1.1"), "rules-v1",
            CourierEtaEstimate.Source.RULES, "TRANSIT_BASELINE"));
    }

    @Test void routePlanRequiresOrderedContiguousStopsAndUniqueReferences() {
        TenantId tenant = TenantId.generate();
        assertThrows(IllegalArgumentException.class, () -> new CourierRoutePlan(UUID.randomUUID(), tenant, "BDG-01",
            "courier-1", LocalDate.parse("2026-10-24"), 1, CourierRoutePlan.Status.DRAFT, now,
            List.of(new CourierRoutePlan.Stop(1, "A", CourierRoutePlan.Stop.StopType.PICKUP, now),
                    new CourierRoutePlan.Stop(3, "B", CourierRoutePlan.Stop.StopType.DELIVERY, now.plusSeconds(300))),
            null, null));
        assertDoesNotThrow(() -> new CourierRoutePlan(UUID.randomUUID(), tenant, "BDG-01", "courier-1",
            LocalDate.parse("2026-10-24"), 1, CourierRoutePlan.Status.DRAFT, now,
            List.of(new CourierRoutePlan.Stop(1, "A", CourierRoutePlan.Stop.StopType.PICKUP, now),
                    new CourierRoutePlan.Stop(2, "B", CourierRoutePlan.Stop.StopType.DELIVERY, now.plusSeconds(300))),
            12000L, 1800));
    }
}
