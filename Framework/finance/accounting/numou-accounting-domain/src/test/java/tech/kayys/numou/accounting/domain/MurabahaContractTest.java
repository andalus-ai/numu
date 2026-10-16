package tech.kayys.syirkah.accounting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.event.MurabahaCreated;
import tech.kayys.syirkah.accounting.domain.event.MurabahaSettled;
import tech.kayys.syirkah.accounting.domain.event.ProfitRecognized;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.MurabahaContract;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MurabahaContract — AAOIFI Lite / PSAK 102")
class MurabahaContractTest {

    private static final TenantRef TENANT = new TenantRef("tenant-acme");
    private static final LedgerId LEDGER = new LedgerId("SHARIAH");

    private static MurabahaContract sampleContract() {
        return MurabahaContract.create(
                "MCT-001", TENANT, LEDGER,
                new BigDecimal("10000.00"),
                new BigDecimal("1200.00"),
                12);
    }

    @Test
    @DisplayName("create() raises MurabahaCreated with correct sellingPrice")
    void createRaisesEvent() {
        MurabahaContract c = sampleContract();
        List<Object> events = c.pullDomainEvents();

        assertEquals(1, events.size());
        assertInstanceOf(MurabahaCreated.class, events.getFirst());

        MurabahaCreated evt = (MurabahaCreated) events.getFirst();
        assertEquals("MCT-001", evt.contractId());
        assertEquals(TENANT,    evt.tenantId());
        assertEquals(LEDGER,    evt.ledgerId());
        assertEquals(0, new BigDecimal("11200.00").compareTo(evt.sellingPrice()));
        assertEquals(12, evt.tenorMonths());
    }

    @Test
    @DisplayName("recognizePeriodProfit() distributes margin evenly each period")
    void recognizePeriodProfit() {
        MurabahaContract c = sampleContract();
        c.pullDomainEvents(); // clear creation event

        c.recognizePeriodProfit();
        List<Object> events = c.pullDomainEvents();

        assertEquals(1, events.size());
        ProfitRecognized pr = (ProfitRecognized) events.getFirst();

        assertEquals("MCT-001", pr.contractId());
        assertEquals(1, pr.periodNumber());
        assertEquals(0, new BigDecimal("100.00").compareTo(pr.recognizedAmount()));
        assertEquals(0, new BigDecimal("1100.00").compareTo(pr.remainingDeferred()));
        assertEquals(1, c.recognizedPeriods());
    }

    @Test
    @DisplayName("settle() recognises all remaining periods and raises MurabahaSettled")
    void settleRecognisesAll() {
        MurabahaContract c = sampleContract();
        c.pullDomainEvents();

        for (int i = 0; i < 10; i++) c.recognizePeriodProfit();
        c.pullDomainEvents();

        c.settle();
        List<Object> events = c.pullDomainEvents();

        // 2 remaining ProfitRecognized + 1 MurabahaSettled
        assertEquals(3, events.size());
        assertInstanceOf(MurabahaSettled.class, events.getLast());
        assertTrue(c.settled());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.deferredProfit()));
    }

    @Test
    @DisplayName("settle() on already settled contract throws")
    void doubleSettleThrows() {
        MurabahaContract c = sampleContract();
        c.settle();
        assertThrows(IllegalStateException.class, c::settle);
    }

    @Test
    @DisplayName("create() with zero cost throws")
    void invalidCostThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                MurabahaContract.create("MCT-X", TENANT, LEDGER,
                        BigDecimal.ZERO, new BigDecimal("100"), 6));
        assertThrows(IllegalArgumentException.class, () ->
                MurabahaContract.create("MCT-X", TENANT, LEDGER,
                        new BigDecimal("-500"), new BigDecimal("100"), 6));
    }

    @Test
    @DisplayName("create() with zero tenor throws")
    void invalidTenorThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                MurabahaContract.create("MCT-X", TENANT, LEDGER,
                        new BigDecimal("10000"), new BigDecimal("1000"), 0));
    }
}
