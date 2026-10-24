package tech.kayys.numou.courier.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CodBranchCloseoutTest {
    private final Instant start = Instant.parse("2026-10-24T00:00:00Z");
    @Test void aggregatesByBranchWindowAndExposesVariance() {
        TenantId tenant = TenantId.generate();
        CodObligation a = obligation(tenant, "BDG-01", "100000");
        a.collect("courier-a", "BDG-01", new BigDecimal("100000"), start.plusSeconds(60));
        a.settle("settlement-a", new BigDecimal("99000"), start.plusSeconds(120));
        CodObligation b = obligation(tenant, "JKT-01", "50000");
        b.collect("courier-b", "JKT-01", new BigDecimal("50000"), start.plusSeconds(60));
        CodBranchCloseout report = CodBranchCloseout.calculate(tenant, "BDG-01", Currency.getInstance("IDR"),
                start, start.plusSeconds(3600), List.of(a, b));
        assertEquals(1, report.obligationCount());
        assertEquals(1, report.varianceCount());
        assertEquals(new BigDecimal("100000"), report.collectedTotal());
        assertEquals(new BigDecimal("99000"), report.settledTotal());
        assertEquals(new BigDecimal("1000"), report.varianceTotal());
    }
    @Test void rejectsCrossTenantAndMixedCurrencyInputs() {
        TenantId tenant = TenantId.generate();
        CodObligation foreign = obligation(TenantId.generate(), "BDG-01", "1000");
        assertThrows(SecurityException.class, () -> CodBranchCloseout.calculate(tenant, "BDG-01", Currency.getInstance("IDR"),
                start, start.plusSeconds(60), List.of(foreign)));
        CodObligation usd = new CodObligation(UUID.randomUUID(), tenant, ShipmentId.generate(), new BigDecimal("10"),
                Currency.getInstance("USD"), start);
        assertThrows(IllegalArgumentException.class, () -> CodBranchCloseout.calculate(tenant, "BDG-01", Currency.getInstance("IDR"),
                start, start.plusSeconds(60), List.of(usd)));
    }
    private CodObligation obligation(TenantId tenant, String branch, String amount) {
        return new CodObligation(UUID.randomUUID(), tenant, ShipmentId.generate(), new BigDecimal(amount),
                Currency.getInstance("IDR"), start);
    }
}
