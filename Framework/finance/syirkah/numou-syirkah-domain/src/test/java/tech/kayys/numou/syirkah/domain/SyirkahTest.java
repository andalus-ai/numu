package tech.kayys.numou.syirkah.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.syirkah.domain.Fixtures.*;

class SyirkahTest {

    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");
    private static final LocalDate FEB28 = LocalDate.parse("2026-02-28");

    private static void eq(long expected, Money actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual.amount()),
                "expected " + expected + " but was " + actual.amount());
    }

    private static CloseSnapshot close(Syirkah s, LocalDate a, LocalDate b, FinancialResult r, ProfitDisposition d) {
        return s.closePeriod(CloseId.generate(), a, b, r, ManagerFault.none(), d, NOW);
    }

    // ---------------------------------------------------------------- allocation

    @Test
    void profitIsSplitByNisbahOnNetProfitNotRevenue() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        // revenue 20M - direct 6M - operating 3.5M - other 1M = net 9.5M
        CloseSnapshot c = close(s, JAN1, JAN31, result(20_000_000, 6_000_000, 3_500_000, 1_000_000),
                ProfitDisposition.DISTRIBUTE);
        eq(9_500_000, c.netResult());
        eq(3_800_000, s.position(A).payable());
        eq(5_700_000, s.position(B).payable());
    }

    @Test
    void smallerProfitSplitsExactly() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(5_000_000), ProfitDisposition.DISTRIBUTE);
        eq(2_000_000, s.position(A).payable());
        eq(3_000_000, s.position(B).payable());
    }

    @Test
    void roundingNeverCreatesOrLosesMoney() {
        Map<PartnerId, BigDecimal> w = new TreeMap<>();
        w.put(A, BigDecimal.ONE);
        w.put(B, BigDecimal.ONE);
        w.put(C, BigDecimal.ONE);
        Map<PartnerId, BigDecimal> parts = Apportionment.apportion(new BigDecimal("100"), w, 0);
        BigDecimal sum = parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100").compareTo(sum));
        assertEquals(0, new BigDecimal("34").compareTo(parts.get(A)));
        Map<PartnerId, BigDecimal> neg = Apportionment.apportion(new BigDecimal("-100"), w, 0);
        assertEquals(0, new BigDecimal("-34").compareTo(neg.get(A)));
    }

    @Test
    void retainedProfitBecomesPartnerCapital() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(9_500_000), ProfitDisposition.RETAIN);
        eq(103_800_000, s.position(A).balance());
        eq(5_700_000, s.position(B).balance());
        eq(0, s.position(A).payable());
        eq(103_800_000, s.position(A).basis());
    }

    @Test
    void lossFollowsCapitalProportionNotNisbah() {
        Syirkah s = open(inan(60_000_000, 40_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, loss(10_000_000), ProfitDisposition.DISTRIBUTE);
        eq(54_000_000, s.position(A).balance());
        eq(36_000_000, s.position(B).balance());
    }

    @Test
    void profitFirstRepairsImpairedCapital() {
        Syirkah s = open(inan(60_000_000, 40_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, loss(10_000_000), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot c = close(s, FEB1, FEB28, profit(14_000_000), ProfitDisposition.DISTRIBUTE);
        eq(60_000_000, s.position(A).balance());
        eq(40_000_000, s.position(B).balance());
        eq(2_000_000, s.position(A).payable());
        eq(2_000_000, s.position(B).payable());
        eq(10_000_000, c.lines().get(0).capitalRestored().add(c.lines().get(1).capitalRestored()));
    }

    @Test
    void mudharabahLossFallsOnCapitalProviderOnly() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        close(s, JAN1, JAN31, loss(8_000_000), ProfitDisposition.DISTRIBUTE);
        eq(92_000_000, s.position(A).balance());
        eq(0, s.position(B).liability());
    }

    @Test
    void mudharabahManagerBearsLossOnlyWhenNegligent() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "ignored agreed purchase limit"), ProfitDisposition.DISTRIBUTE, NOW);
        eq(95_000_000, s.position(A).balance());
        eq(3_000_000, s.position(B).liability());
        s.settleLiability(B, idr(3_000_000), "r1", NOW);
        eq(0, s.position(B).liability());
    }

    @Test
    void faultWithoutLossOrOutsideMudharabahIsRejected() {
        Syirkah m = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        assertThrows(BusinessRuleViolation.class, () -> m.closePeriod(CloseId.generate(), JAN1, JAN31,
                profit(1_000_000), ManagerFault.of(idr(1), "x"), ProfitDisposition.DISTRIBUTE, NOW));
        Syirkah i = open(inan(1_000_000, 1_000_000, ratio(A, 50, B, 50)));
        assertThrows(BusinessRuleViolation.class, () -> i.closePeriod(CloseId.generate(), JAN1, JAN31,
                loss(100), ManagerFault.of(idr(100), "x"), ProfitDisposition.DISTRIBUTE, NOW));
    }

    // ---------------------------------------------------------------- contract validity

    @Test
    void revenueBasedProfitIsInvalid() {
        SyirkahContract c = inan(1, 1, ratio(A, 50, B, 50));
        SyirkahContract bad = new SyirkahContract(1, c.type(), c.currency(), c.partners(), c.capital(), c.nisbah(),
                ProfitBasis.REVENUE, null, Set.of(), c.witnesses(), true, c.effectiveFrom());
        assertTrue(bad.validate().stream().anyMatch(v -> v.code().equals("PROFIT_ON_REVENUE")));
    }

    @Test
    void nisbahMustSumToExactlyHundredPercent() {
        assertThrows(IllegalArgumentException.class, () -> ratio(A, 40, B, 59));
        assertThrows(IllegalArgumentException.class, () -> ratio(A, 0, B, 100));
    }

    @Test
    void forbiddenClausesWitnessesAndIjabQabulAreChecked() {
        SyirkahContract c = inan(10, 10, ratio(A, 50, B, 50));
        SyirkahContract bad = new SyirkahContract(1, c.type(), c.currency(), c.partners(), c.capital(), c.nisbah(),
                ProfitBasis.NET_PROFIT, null, Set.of(ForbiddenClause.FIXED_PROFIT_AMOUNT, ForbiddenClause.CAPITAL_GUARANTEE),
                List.of("only one"), false, c.effectiveFrom());
        List<String> codes = bad.validate().stream().map(ContractViolation::code).toList();
        assertTrue(codes.contains("FORBIDDEN_FIXED_PROFIT_AMOUNT"));
        assertTrue(codes.contains("FORBIDDEN_CAPITAL_GUARANTEE"));
        assertTrue(codes.contains("WITNESSES"));
        assertTrue(codes.contains("IJAB_QABUL"));
        assertThrows(BusinessRuleViolation.class, bad::requireValid);
    }

    @Test
    void explicitLossSharingIsForbiddenForCapitalBasedTypes() {
        SyirkahContract c = inan(10, 10, ratio(A, 50, B, 50));
        Map<PartnerId, BigDecimal> ls = new TreeMap<>();
        ls.put(A, new BigDecimal("0.9"));
        ls.put(B, new BigDecimal("0.1"));
        SyirkahContract bad = new SyirkahContract(1, c.type(), c.currency(), c.partners(), c.capital(), c.nisbah(),
                ProfitBasis.NET_PROFIT, ls, Set.of(), c.witnesses(), true, c.effectiveFrom());
        assertTrue(bad.validate().stream().anyMatch(v -> v.code().equals("LOSS_NOT_BY_CAPITAL")));
    }

    @Test
    void mudharabahManagerCannotContributeCapital() {
        SyirkahContract c = mudharabah(100, ratio(A, 70, B, 30));
        SyirkahContract bad = new SyirkahContract(1, c.type(), c.currency(), c.partners(),
                List.of(CapitalContribution.cash(A, idr(100)), CapitalContribution.cash(B, idr(10))),
                c.nisbah(), ProfitBasis.NET_PROFIT, null, Set.of(), c.witnesses(), true, c.effectiveFrom());
        assertTrue(bad.validate().stream().anyMatch(v -> v.code().equals("MUDHARABAH_MANAGER_CAPITAL")));
    }

    @Test
    void abdanHasNoCashCapital() {
        SyirkahContract c = new SyirkahContract(1, SyirkahType.ABDAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, JAN1);
        assertTrue(c.validate().isEmpty());
        Syirkah s = open(c);
        close(s, JAN1, JAN31, loss(1_000), ProfitDisposition.DISTRIBUTE);
        eq(500, s.position(A).balance().negate()); // no capital: loss weights fall back to nisbah
    }

    // ---------------------------------------------------------------- distribution

    @Test
    void distributionIsIdempotentAndBoundedByPayable() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(9_500_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(1_000_000), "pay-1", NOW);
        s.distributeProfit(A, idr(1_000_000), "pay-1", NOW); // replay: no effect
        eq(2_800_000, s.position(A).payable());
        assertThrows(BusinessRuleViolation.class, () -> s.distributeProfit(A, idr(5_000_000), "pay-2", NOW));
        assertThrows(BusinessRuleViolation.class, () -> s.distributeProfit(A, idr(2), "pay-1", NOW));
    }

    // ---------------------------------------------------------------- revision

    @Test
    void changingNisbahNeedsCloseAndUnanimity() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR, s.contract().partners(),
                List.of(CapitalContribution.cash(A, idr(103_800_000)), CapitalContribution.cash(B, idr(5_700_000))),
                ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, FEB1);
        assertThrows(BusinessRuleViolation.class, () -> s.reviseContract(next, Set.of(A, B), NOW)); // not closed

        close(s, JAN1, JAN31, profit(9_500_000), ProfitDisposition.RETAIN);
        assertThrows(BusinessRuleViolation.class, () -> s.reviseContract(next, Set.of(A), NOW)); // not unanimous
        s.reviseContract(next, Set.of(A, B), NOW);
        assertEquals(2, s.contract().version());
        eq(103_800_000, s.position(A).balance()); // no extra movement needed
        CloseSnapshot c2 = close(s, FEB1, FEB28, profit(1_000_000), ProfitDisposition.DISTRIBUTE);
        eq(500_000, s.position(B).payable());
        assertEquals(2, c2.contractVersion());
        assertEquals(1, s.closes().get(0).contractVersion());
    }

    @Test
    void capitalAdditionAndPartnerReplacementProduceMovements() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(5_000_000), "p1", NOW);
        s.distributeProfit(B, idr(5_000_000), "p2", NOW);
        // B leaves, C joins with 50M, A adds 20M
        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(C, "C", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(120_000_000)), CapitalContribution.cash(C, idr(50_000_000))),
                ratio(A, 60, C, 40), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, FEB1);
        s.reviseContract(next, Set.of(A, B, C), NOW);
        eq(120_000_000, s.position(A).balance());
        eq(50_000_000, s.position(C).balance());
        eq(0, s.position(B).balance());
        eq(0, s.position(B).basis());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void typeCannotChangeByRevision() {
        Syirkah s = open(inan(10, 10, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        SyirkahContract m = new SyirkahContract(2, SyirkahType.MUDHARABAH, IDR,
                List.of(new Partner(A, "A", PartnerRole.CAPITAL_PROVIDER, false),
                        new Partner(B, "B", PartnerRole.MANAGER, true)),
                List.of(CapitalContribution.cash(A, idr(20))), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null,
                Set.of(), List.of("W1", "W2"), true, FEB1);
        assertThrows(BusinessRuleViolation.class, () -> s.reviseContract(m, Set.of(A, B), NOW));
    }

    // ---------------------------------------------------------------- correction

    @Test
    void correctionReversesAndRecomputes() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        CloseSnapshot c = close(s, JAN1, JAN31, profit(9_500_000), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot fixed = s.correctLatestClose(c.id(), CloseId.generate(), profit(10_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);
        eq(4_000_000, s.position(A).payable());
        eq(6_000_000, s.position(B).payable());
        assertEquals(CloseStatus.REVERSED, s.close(c.id()).status());
        assertEquals(c.id(), fixed.supersedes());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void correctionAfterDistributionTurnsOverpaymentIntoLiability() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        CloseSnapshot c = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(4_000_000), "pa", NOW);
        s.distributeProfit(B, idr(6_000_000), "pb", NOW);
        // the profit was really 5M: A entitled 2M (paid 4M), B entitled 3M (paid 6M)
        s.correctLatestClose(c.id(), CloseId.generate(), profit(5_000_000), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, NOW);
        eq(0, s.position(A).payable());
        eq(0, s.position(B).payable());
        eq(2_000_000, s.position(A).liability());
        eq(3_000_000, s.position(B).liability());
        s.settleLiability(A, idr(2_000_000), "back-a", NOW);
        eq(0, s.position(A).liability());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void correctionUpwardAfterPartialDistributionLeavesRemainderPayable() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        CloseSnapshot c = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(1_000_000), "pa", NOW);
        s.correctLatestClose(c.id(), CloseId.generate(), profit(20_000_000), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, NOW);
        eq(7_000_000, s.position(A).payable()); // 8M entitled - 1M paid
        eq(12_000_000, s.position(B).payable());
        eq(0, s.position(A).liability());
    }

    @Test
    void correctionRefusedAfterLiabilitySettlement() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        CloseSnapshot c = s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "breach"), ProfitDisposition.DISTRIBUTE, NOW);
        s.settleLiability(B, idr(1_000_000), "s1", NOW);
        assertThrows(BusinessRuleViolation.class, () -> s.correctLatestClose(c.id(), CloseId.generate(),
                loss(8_000_000), ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW));
    }

    @Test
    void overlappingPeriodsRejected() {
        Syirkah s = open(inan(100, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        assertThrows(BusinessRuleViolation.class, () -> close(s, JAN31, FEB28, profit(10), ProfitDisposition.DISTRIBUTE));
    }

    // ---------------------------------------------------------------- liquidation

    @Test
    void liquidationProfitPaysDebtsFirstThenCapitalPlusNisbahProfit() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(200_000_000), idr(20_000_000), true, FEB28),
                ManagerFault.none(), NOW);
        eq(180_000_000, r.residual());
        eq(30_000_000, r.netResult());
        eq(115_000_000, r.payouts().get(0).cashReceived());
        eq(65_000_000, r.payouts().get(1).cashReceived());
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        eq(0, s.position(A).basis());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void liquidationLossIsBorneByCapitalProportion() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(140_000_000), idr(20_000_000), true, FEB28),
                ManagerFault.none(), NOW);
        eq(-30_000_000, r.netResult());
        eq(80_000_000, r.payouts().get(0).cashReceived());
        eq(40_000_000, r.payouts().get(1).cashReceived());
    }

    @Test
    void liquidationBeyondCapitalLeavesPartnerDeficit() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(0), idr(30_000_000), true, FEB28),
                ManagerFault.none(), NOW);
        // residual -30M, claims 150M => loss 180M; A bears 120M of 100M capital => owes 20M; B 60M of 50M => owes 10M
        eq(20_000_000, r.payouts().get(0).owed());
        eq(10_000_000, r.payouts().get(1).owed());
        eq(20_000_000, s.position(A).liability());
        eq(0, s.position(A).balance());
    }

    @Test
    void liquidationRequiresStartShufahAndIsFinal() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        LiquidationInput in = new LiquidationInput(idr(200), idr(0), true, FEB28);
        assertThrows(RuntimeException.class, () -> s.liquidate(in, null, NOW));
        s.startLiquidation(NOW);
        assertThrows(BusinessRuleViolation.class, () -> s.liquidate(
                new LiquidationInput(idr(200), idr(0), false, FEB28), null, NOW));
        s.liquidate(in, null, NOW);
        assertThrows(RuntimeException.class, () -> s.liquidate(in, null, NOW));
        assertThrows(RuntimeException.class, () -> close(s, FEB1, FEB28, profit(1), ProfitDisposition.DISTRIBUTE));
    }

    @Test
    void undistributedProfitCoversLaterLossAtLiquidation() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE); // payable A4M B6M
        s.startLiquidation(NOW);
        // assets 95M no debts: claims = 100M + 10M = 110M, loss 15M all on A's capital (B has none)
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(95_000_000), idr(0), true, FEB28), null, NOW);
        eq(-15_000_000, r.netResult());
        eq(89_000_000, r.payouts().get(0).cashReceived()); // 100M capital + 4M profit - 15M loss
        eq(6_000_000, r.payouts().get(1).cashReceived());
        eq(95_000_000, r.payouts().get(0).cashReceived().add(r.payouts().get(1).cashReceived()));
    }

    // ---------------------------------------------------------------- accounting / audit

    @Test
    void everyMovementPostsBalancedJournalLines() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, loss(9_000_000), ProfitDisposition.DISTRIBUTE);
        close(s, FEB1, FEB28, profit(30_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(1_000_000), "p", NOW);
        for (PartnerMovement m : s.movements()) {
            BigDecimal d = BigDecimal.ZERO;
            BigDecimal c = BigDecimal.ZERO;
            for (SyirkahPostingPlan.Line l : SyirkahPostingPlan.forMovement(m)) {
                d = d.add(l.debit().amount());
                c = c.add(l.credit().amount());
            }
            assertEquals(0, d.compareTo(c));
        }
    }

    @Test
    void periodResultAccountEqualsNetResultOfTheClose() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, loss(9_000_000), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot c = close(s, FEB1, FEB28, profit(30_000_000), ProfitDisposition.RETAIN);
        BigDecimal net = BigDecimal.ZERO;
        for (PartnerMovement m : s.movements()) {
            if (c.id().equals(m.closeId())) {
                for (SyirkahPostingPlan.Line l : SyirkahPostingPlan.forMovement(m)) {
                    if (l.account() == SyirkahPostingPlan.AccountRole.PERIOD_RESULT) {
                        net = net.add(l.debit().amount()).subtract(l.credit().amount());
                    }
                }
            }
        }
        assertEquals(0, net.compareTo(c.netResult().amount()));
    }

    @Test
    void auditChainsAreIntactAndDetectTampering() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        CloseSnapshot c = close(s, JAN1, JAN31, profit(9_000_000), ProfitDisposition.DISTRIBUTE);
        assertTrue(s.verifyIntegrity().isEmpty());
        assertTrue(c.hashIsValid());
        PartnerMovement m = s.movements().get(0);
        PartnerMovement forged = new PartnerMovement(m.id(), m.sequence(), m.partner(), m.type(), idr(1),
                m.closeId(), m.reference(), m.occurredAt(), m.previousHash(), m.hash());
        assertFalse(forged.hashIsValid());
    }

    @Test
    void domainEventsAreRaised() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        assertEquals(2, s.pullDomainEvents().size());
        assertEquals(0, s.pullDomainEvents().size());
    }

    @Test
    void amountsWithTooManyDecimalsAreRejected() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        FinancialResult r = FinancialResult.of(Money.of(new BigDecimal("10.5"), IDR), idr(0), idr(0), idr(0));
        assertThrows(BusinessRuleViolation.class, () -> close(s, JAN1, JAN31, r, ProfitDisposition.DISTRIBUTE));
    }

    // ---------------------------------------------------------------- reports / reconciliation

    @Test
    void reconciliationHoldsThroughTheWholeLifecycle() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        assertTrue(s.reconcile().balanced());
        CloseSnapshot c1 = close(s, JAN1, JAN31, loss(9_000_000), ProfitDisposition.DISTRIBUTE);
        assertTrue(s.reconcile().balanced());
        CloseSnapshot c2 = close(s, FEB1, FEB28, profit(30_000_000), ProfitDisposition.RETAIN);
        assertTrue(s.reconcile().balanced());
        s.correctLatestClose(c2.id(), CloseId.generate(), profit(12_000_000), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, NOW);
        assertTrue(s.reconcile().balanced());
        s.distributeProfit(A, idr(1_000_000), "p", NOW);
        assertTrue(s.reconcile().balanced());
        s.startLiquidation(NOW);
        s.liquidate(new LiquidationInput(idr(10_000_000), idr(40_000_000), true, FEB28), ManagerFault.none(), NOW);
        EquityReconciliation r = s.reconcile();
        assertTrue(r.balanced(), "difference " + r.difference().amount());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void reconciliationHoldsForMudharabahFaultAndOverpayment() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "breach"), ProfitDisposition.DISTRIBUTE, NOW);
        assertTrue(s.reconcile().balanced());
        s.settleLiability(B, idr(3_000_000), "s", NOW);
        CloseSnapshot c = close(s, FEB1, FEB28, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(1_500_000), "d1", NOW);
        s.distributeProfit(B, idr(1_500_000), "d2", NOW);
        assertTrue(s.reconcile().balanced());
        s.correctLatestClose(c.id(), CloseId.generate(), profit(2_000_000), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE, NOW);
        assertTrue(s.reconcile().balanced());
    }

    @Test
    void statementShowsRunningBalances() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(1_000_000), "p", NOW);
        PartnerStatement st = s.statement(A);
        assertEquals(3, st.lines().size()); // contribution, profit allocated, distribution
        eq(100_000_000, st.lines().get(0).capitalBalance());
        eq(4_000_000, st.lines().get(1).profitPayable());
        eq(3_000_000, st.lines().get(2).profitPayable());
        eq(3_000_000, st.closing().payable());
        assertEquals("A", st.name());
    }

    @Test
    void revisionResetsImpairedCapitalBasisToTheBalance() {
        // regression: BASIS_CLOSED once had the wrong sign and inflated the basis instead of resetting it
        Syirkah s = open(inan(60_000_000, 40_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, loss(10_000_000), ProfitDisposition.DISTRIBUTE); // A 54M, B 36M of 60M/40M
        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR, s.contract().partners(),
                List.of(CapitalContribution.cash(A, idr(54_000_000)), CapitalContribution.cash(B, idr(36_000_000))),
                ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, FEB1);
        s.reviseContract(next, Set.of(A, B), NOW);
        eq(54_000_000, s.position(A).balance());
        eq(54_000_000, s.position(A).basis());
        eq(36_000_000, s.position(B).basis());
        // new baseline: a later profit has nothing to repair, it is all distributable by nisbah
        close(s, FEB1, FEB28, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        eq(5_000_000, s.position(A).payable());
        eq(5_000_000, s.position(B).payable());
        assertTrue(s.reconcile().balanced());
    }
}
