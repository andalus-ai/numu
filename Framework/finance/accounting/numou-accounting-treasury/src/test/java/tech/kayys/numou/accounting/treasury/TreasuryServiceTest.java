package tech.kayys.syirkah.accounting.treasury;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TreasuryServiceTest {
    @Test
    void enforcesPaymentBatchApprovalAndSettlement() {
        var treasury = new TreasuryService();
        var account = treasury.registerAccount("tenant-a", "idr", "****1234");
        var payment = new TreasuryService.Payment(
                java.util.UUID.randomUUID(), account.id(), new BigDecimal("125.50"),
                "IDR", "Supplier A", "INV-1");

        var batch = treasury.createBatch("tenant-a", List.of(payment));
        assertEquals(new BigDecimal("125.50"), batch.total());
        var approved = treasury.approve(batch.id());
        var submitted = treasury.submit(approved.id());
        assertEquals(TreasuryService.BatchStatus.SETTLED, treasury.settle(submitted.id()).status());
    }

    @Test
    void rejectsCrossTenantPayments() {
        var treasury = new TreasuryService();
        var account = treasury.registerAccount("tenant-a", "idr", "****1234");
        var payment = new TreasuryService.Payment(
                java.util.UUID.randomUUID(), account.id(), BigDecimal.ONE,
                "IDR", "Supplier A", "INV-1");

        assertThrows(IllegalArgumentException.class,
                () -> treasury.createBatch("tenant-b", List.of(payment)));
    }

    @Test
    void supportsForecastNettingFxDebtAndCashViews() {
        var treasury = new TreasuryService();
        var account = treasury.registerAccount("tenant-a", "idr", "****1234");
        var position = treasury.recordCash(account.id(), new BigDecimal("1000"),
                new BigDecimal("900"), new BigDecimal("950"));
        assertEquals(new BigDecimal("100"), position.variance());

        var forecast = treasury.forecastDirect(java.util.UUID.randomUUID(), LocalDate.of(2026, 1, 1),
                2, "IDR", (category, start, end) -> BigDecimal.TEN);
        assertEquals(12, forecast.lines().size());
        assertEquals(BigDecimal.valueOf(120), forecast.net());

        var net = treasury.net(List.of(
                new TreasuryService.NettingPosition("A", "IDR", new BigDecimal("100"), BigDecimal.ZERO),
                new TreasuryService.NettingPosition("B", "IDR", BigDecimal.ZERO, new BigDecimal("100"))));
        assertEquals(BigDecimal.ZERO, net.residual());

        var exposure = treasury.recordExposure("USD", "IDR", new BigDecimal("1000"),
                LocalDate.of(2026, 6, 30));
        var hedged = treasury.designateHedge(exposure.id(), new BigDecimal("800"));
        assertEquals(new BigDecimal("200"), hedged.unhedgedAmount());

        var debt = treasury.createDebt("IDR", new BigDecimal("10000"), new BigDecimal("0.12"),
                LocalDate.of(2027, 1, 1));
        treasury.draw(debt.id(), new BigDecimal("5000"));
        treasury.accrueInterest(debt.id(), 30);
        assertTrue(treasury.repay(debt.id(), new BigDecimal("1000")).drawn().compareTo(new BigDecimal("4000")) == 0);
    }
}
