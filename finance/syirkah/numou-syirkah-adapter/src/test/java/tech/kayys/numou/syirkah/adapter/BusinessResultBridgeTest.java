package tech.kayys.numou.numouadapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouadapter.bridge.BusinessResultBridge;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numoudomain.FinancialResult;
import tech.kayys.numou.numoudomain.ProfitDisposition;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BusinessResultBridgeTest {

    private static SyirkahBusinessResult result(String id) {
        return new SyirkahBusinessResult(id, "CT-1", 1, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                new BigDecimal("20000"), new BigDecimal("6000"), new BigDecimal("3500"), new BigDecimal("1000"),
                "USD", "evidence-1");
    }

    @Test
    void netResultIsPreservedExactlyAcrossTheBridge() {
        SyirkahBusinessResult r = result("R1");
        FinancialResult fr = BusinessResultBridge.toFinancialResult(r);
        assertEquals(0, r.netResult().compareTo(fr.netResult().amount()));
        assertEquals("USD", fr.revenue().currency().code());
    }

    @Test
    void identifiersAreDeterministicAndDistinct() {
        assertEquals(BusinessResultBridge.closeId(result("R1")), BusinessResultBridge.closeId(result("R1")));
        assertNotEquals(BusinessResultBridge.closeId(result("R1")), BusinessResultBridge.closeId(result("R2")));
        assertEquals(BusinessResultBridge.partnerId("CT-1", "P-A"), BusinessResultBridge.partnerId("CT-1", "P-A"));
        assertNotEquals(BusinessResultBridge.partnerId("CT-1", "P-A"), BusinessResultBridge.partnerId("CT-2", "P-A"));
    }

    @Test
    void closeCommandCarriesPeriodAndDisposition() {
        TenantId t = TenantId.generate();
        Commands.ClosePeriod cmd = BusinessResultBridge.toClosePeriod(t, "CO-1", result("R1"), null,
                ProfitDisposition.RETAIN);
        assertEquals(LocalDate.parse("2026-01-31"), cmd.end());
        assertEquals(ProfitDisposition.RETAIN, cmd.disposition());
        assertEquals(BusinessResultBridge.syirkahId(t.value().toString(), "CO-1", "CT-1"), cmd.id());
    }
}
