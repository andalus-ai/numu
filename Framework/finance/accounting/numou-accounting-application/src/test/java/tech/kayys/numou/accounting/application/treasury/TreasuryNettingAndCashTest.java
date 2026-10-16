
package tech.kayys.syirkah.accounting.application.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.treasury.NettingEngine;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.treasury.CashPosition;
import tech.kayys.syirkah.accounting.domain.treasury.PaymentBatch;
import tech.kayys.syirkah.accounting.domain.treasury.PaymentBatchStatus;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Advanced Treasury Platform Cash Position & Netting Test")
class TreasuryNettingAndCashTest {

    @Test
    @DisplayName("Cash position tracks opening, inflows, outflows, and closing balance")
    void testCashPosition() {
        CashPosition position = new CashPosition(
                "POS-01", "BA-BCA-01", LocalDate.now(),
                Money.of(new BigDecimal("100000000"), "IDR")
        );

        position.recordInflow(Money.of(new BigDecimal("50000000"), "IDR"));
        position.recordOutflow(Money.of(new BigDecimal("30000000"), "IDR"));

        // 100M + 50M - 30M = 120M
        assertEquals(0, new BigDecimal("120000000").compareTo(position.closingBalance().amount()));
    }

    @Test
    @DisplayName("Payment batch approval and execution lifecycle")
    void testPaymentBatch() {
        PaymentBatch batch = new PaymentBatch("BATCH-01", "BA-MANDIRI-01");
        batch.addItem(new PaymentBatch.PaymentItem("P1", "Vendor A", "ID123", Money.of(new BigDecimal("10000000"), "IDR")));
        batch.addItem(new PaymentBatch.PaymentItem("P2", "Vendor B", "ID456", Money.of(new BigDecimal("20000000"), "IDR")));

        assertEquals(0, new BigDecimal("30000000").compareTo(batch.totalBatchAmount("IDR").amount()));
        assertEquals(PaymentBatchStatus.DRAFT, batch.status());

        batch.approve("TREASURY-MGR");
        assertEquals(PaymentBatchStatus.APPROVED, batch.status());

        batch.execute();
        assertEquals(PaymentBatchStatus.EXECUTED, batch.status());
    }

    @Test
    @DisplayName("Multilateral netting reduces bilateral gross obligations to net positions")
    void testMultilateralNetting() {
        TenantRef entityA = new TenantRef("entity-a");
        TenantRef entityB = new TenantRef("entity-b");
        TenantRef entityC = new TenantRef("entity-c");

        // A owes B 100M
        // B owes C 60M
        // C owes A 40M
        var obligations = List.of(
                new NettingEngine.IntercompanyObligation(entityA, entityB, Money.of(new BigDecimal("100000000"), "IDR")),
                new NettingEngine.IntercompanyObligation(entityB, entityC, Money.of(new BigDecimal("60000000"), "IDR")),
                new NettingEngine.IntercompanyObligation(entityC, entityA, Money.of(new BigDecimal("40000000"), "IDR"))
        );

        NettingEngine engine = new NettingEngine();
        var settlements = engine.calculateMultilateralNetting(obligations, "IDR");

        // Entity A: -100M + 40M = -60M (net payer)
        // Entity B: +100M - 60M = +40M (net receiver)
        // Entity C: +60M - 40M  = +20M (net receiver)
        assertEquals(3, settlements.size());

        for (var s : settlements) {
            if (s.entity().equals(entityA)) {
                assertEquals(0, new BigDecimal("-60000000").compareTo(s.netAmount().amount()));
            } else if (s.entity().equals(entityB)) {
                assertEquals(0, new BigDecimal("40000000").compareTo(s.netAmount().amount()));
            } else if (s.entity().equals(entityC)) {
                assertEquals(0, new BigDecimal("20000000").compareTo(s.netAmount().amount()));
            }
        }
    }
}
