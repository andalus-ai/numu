package tech.kayys.numou.syirkah.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void explicitLossSharingCannotImpairMoreCapitalThanPartnerHas() {
        SyirkahContract contract = new SyirkahContract(1, SyirkahType.ABDAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, new BigDecimal("0.9"), B, new BigDecimal("0.1")),
                Set.of(), List.of("W1", "W2"), true, JAN1);
        Syirkah s = open(contract);
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);

        CloseSnapshot lossClose = close(s, FEB1, FEB28, loss(10_000_000), ProfitDisposition.DISTRIBUTE);

        // The 90% contractual share would be 9M, but A has only 5M capital.
        // The excess 4M remains an entity deficit; it is not charged to B or A as debt.
        eq(0, s.position(A).balance());
        eq(4_000_000, s.position(B).balance());
        eq(4_000_000, lossClose.unallocatedLoss());
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void lossesBeyondCapitalBecomeEntityDeficitWithoutNegativeEquity() {
        Syirkah s = open(inan(100, 0, ratio(A, 50, B, 50)));
        CloseSnapshot close = close(s, JAN1, JAN31, loss(101), ProfitDisposition.DISTRIBUTE);
        eq(1, close.unallocatedLoss());
        eq(0, s.position(A).balance());
        eq(0, s.position(B).balance());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void excessLossIsRecordedAsEntityDeficitNotPartnerLiability() {
        Syirkah s = open(inan(100, 0, ratio(A, 50, B, 50)));
        CloseSnapshot close = close(s, JAN1, JAN31, loss(125), ProfitDisposition.DISTRIBUTE);

        eq(25, close.unallocatedLoss());
        eq(0, s.position(A).balance());
        eq(0, s.position(B).balance());
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
        assertTrue(close.hashIsValid());
    }

    @Test
    void exhaustedMudharabahRecordsUncoveredLossWithoutChargingManager() {
        Syirkah s = open(mudharabah(100, ratio(A, 70, B, 30)));
        CloseSnapshot first = close(s, JAN1, JAN31, loss(100), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot second = close(s, FEB1, FEB28, loss(10), ProfitDisposition.DISTRIBUTE);

        eq(0, first.unallocatedLoss());
        eq(10, second.unallocatedLoss());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void exhaustedMudharabahCapitalLeavesFurtherOrdinaryLossAtEntityLevel() {
        Syirkah s = open(mudharabah(100, ratio(A, 70, B, 30)));
        close(s, JAN1, JAN31, loss(100), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot second = close(s, FEB1, FEB28, loss(1), ProfitDisposition.DISTRIBUTE);
        eq(0, s.position(A).balance());
        eq(1, second.unallocatedLoss());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
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
    void periodCloseRejectsAmountsAndManagerFaultInWrongCurrency() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        var usd = tech.kayys.numou.foundation.domain.valueobject.Currency.of("USD");
        Money usdAmount = Money.of(BigDecimal.ONE, usd);
        FinancialResult wrongCurrency = FinancialResult.of(usdAmount, Money.zero(usd),
                Money.zero(usd), Money.zero(usd));

        assertThrows(BusinessRuleViolation.class, () -> s.closePeriod(CloseId.generate(), JAN1, JAN31,
                wrongCurrency, ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW));
        assertThrows(BusinessRuleViolation.class, () -> s.closePeriod(CloseId.generate(), JAN1, JAN31,
                loss(1_000), ManagerFault.of(usdAmount, "documented breach"),
                ProfitDisposition.DISTRIBUTE, NOW));
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
    void contractCanonicalBindsAccountingAndAkadTerms() {
        SyirkahContract base = inan(10, 10, ratio(A, 50, B, 50));
        SyirkahContract withForbiddenClause = new SyirkahContract(base.version(), base.type(), base.currency(),
                base.partners(), base.capital(), base.nisbah(), base.profitBasis(), base.lossSharing(),
                Set.of(ForbiddenClause.CAPITAL_GUARANTEE), base.witnesses(), base.ijabQabulRecorded(),
                base.effectiveFrom());
        assertFalse(base.canonical().equals(withForbiddenClause.canonical()),
                "a declared forbidden clause must be bound into the contract fingerprint");

        SyirkahContract changedPartnerRole = new SyirkahContract(base.version(), base.type(), base.currency(),
                List.of(new Partner(A, "A", PartnerRole.PARTNER, false), base.partners().get(1)),
                base.capital(), base.nisbah(), base.profitBasis(), base.lossSharing(), base.declaredClauses(),
                base.witnesses(), base.ijabQabulRecorded(), base.effectiveFrom());
        assertFalse(base.canonical().equals(changedPartnerRole.canonical()),
                "partner role/work terms must be bound into the contract fingerprint");

        SyirkahContract reordered = new SyirkahContract(base.version(), base.type(), base.currency(),
                List.of(base.partners().get(1), base.partners().get(0)),
                List.of(base.capital().get(1), base.capital().get(0)), base.nisbah(), base.profitBasis(),
                base.lossSharing(), base.declaredClauses(), List.of("W2", "W1"),
                base.ijabQabulRecorded(), base.effectiveFrom());
        assertEquals(base.canonical(), reordered.canonical(),
                "canonical contract fingerprint must not depend on collection ordering");
    }

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
        eq(0, s.position(A).balance()); // ABDAN has no cash capital to impair
        eq(1_000, s.closes().get(0).unallocatedLoss()); // uncovered loss remains at entity level
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void explicitAbdanLossWeightsCannotCreateNegativePartnerCapital() {
        SyirkahContract c = new SyirkahContract(1, SyirkahType.ABDAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, new BigDecimal("0.8"), B, new BigDecimal("0.2")),
                Set.of(), List.of("W1", "W2"), true, JAN1);
        assertTrue(c.validate().isEmpty());

        Syirkah s = open(c);
        CloseSnapshot close = close(s, JAN1, JAN31, loss(1_000), ProfitDisposition.DISTRIBUTE);

        eq(1_000, close.unallocatedLoss());
        eq(0, s.position(A).balance());
        eq(0, s.position(B).balance());
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
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
    void contractRevisionRejectsNullTimestampWithoutChangingAggregate() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(1_000_000), ProfitDisposition.RETAIN);
        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR,
                s.contract().partners(), s.contract().capital(), ratio(A, 50, B, 50),
                ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, FEB1);
        int movementCount = s.movements().size();
        int contractCount = s.contractHistory().size();
        String movementHead = s.movements().get(s.movements().size() - 1).hash();

        assertThrows(NullPointerException.class, () -> s.reviseContract(next, Set.of(A, B), null));

        assertEquals(movementCount, s.movements().size());
        assertEquals(contractCount, s.contractHistory().size());
        assertEquals(1, s.contract().version());
        assertEquals(movementHead, s.movements().get(s.movements().size() - 1).hash());
        assertTrue(s.verifyIntegrity().isEmpty());
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
    void partnerExitWithUnpaidProfitIsRejectedAtomicallyUntilPayableIsSettled() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);

        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(C, "C", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)),
                        CapitalContribution.cash(C, idr(50_000_000))),
                ratio(A, 50, C, 50), ProfitBasis.NET_PROFIT, null, Set.of(),
                List.of("W1", "W2"), true, FEB1);
        int movementCount = s.movements().size();
        int contractCount = s.contractHistory().size();
        Money capitalB = s.position(B).balance();
        Money payableB = s.position(B).payable();

        assertThrows(BusinessRuleViolation.class, () -> s.reviseContract(next, Set.of(A, B, C), NOW));
        assertEquals(movementCount, s.movements().size());
        assertEquals(contractCount, s.contractHistory().size());
        assertEquals(0, capitalB.compareTo(s.position(B).balance()));
        assertEquals(0, payableB.compareTo(s.position(B).payable()));
        assertEquals(1, s.contract().version());
        assertTrue(s.verifyIntegrity().isEmpty());

        s.distributeProfit(A, idr(5_000_000), "exit-a-profit", NOW);
        s.distributeProfit(B, idr(5_000_000), "exit-b-profit", NOW);
        s.reviseContract(next, Set.of(A, B, C), NOW);

        eq(100_000_000, s.position(A).balance());
        eq(0, s.position(B).balance());
        eq(0, s.position(B).basis());
        eq(50_000_000, s.position(C).balance());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void rejectedPartnerExitDoesNotPartiallyApplyEarlierCapitalAdjustments() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "documented breach"), ProfitDisposition.DISTRIBUTE, NOW);

        // A would receive a capital return under the proposed contract, but B is leaving
        // with an unsettled manager-fault liability. The whole revision must be rejected
        // before any movement or contract history is changed.
        SyirkahContract next = new SyirkahContract(2, SyirkahType.MUDHARABAH, IDR,
                List.of(new Partner(A, "A", PartnerRole.CAPITAL_PROVIDER, false),
                        new Partner(C, "C", PartnerRole.MANAGER, true)),
                List.of(CapitalContribution.cash(A, idr(90_000_000))),
                ratio(A, 70, C, 30), ProfitBasis.NET_PROFIT, null, Set.of(),
                List.of("W1", "W2"), true, FEB1);
        int movementCount = s.movements().size();
        int contractCount = s.contractHistory().size();
        Money capitalA = s.position(A).balance();
        Money liabilityB = s.position(B).liability();

        assertThrows(BusinessRuleViolation.class, () -> s.reviseContract(next, Set.of(A, B, C), NOW));

        assertEquals(movementCount, s.movements().size());
        assertEquals(contractCount, s.contractHistory().size());
        assertEquals(0, capitalA.compareTo(s.position(A).balance()));
        assertEquals(0, liabilityB.compareTo(s.position(B).liability()));
        assertEquals(1, s.contract().version());
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
    void correctingAnUnallocatedLossReplacesTheEntityDeficitInsteadOfAccumulatingIt() {
        SyirkahContract contract = new SyirkahContract(1, SyirkahType.ABDAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, new BigDecimal("0.5"), B, new BigDecimal("0.5")),
                Set.of(), List.of("W1", "W2"), true, JAN1);
        Syirkah s = open(contract);
        CloseSnapshot original = close(s, JAN1, JAN31, loss(1_000), ProfitDisposition.DISTRIBUTE);

        eq(1_000, original.unallocatedLoss());
        assertTrue(s.reconcile().balanced());

        CloseSnapshot corrected = s.correctLatestClose(original.id(), CloseId.generate(), loss(2_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);

        assertEquals(CloseStatus.REVERSED, s.close(original.id()).status());
        eq(2_000, corrected.unallocatedLoss());
        assertEquals(original.id(), corrected.supersedes());
        assertTrue(s.reconcile().balanced(), "reconciliation must use only the active corrected deficit");
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void invalidCorrectionDoesNotReverseTheExistingCloseOrMutateMovements() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        CloseSnapshot original = s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);
        int movementCount = s.movements().size();
        Money capitalA = s.position(A).balance();
        Money capitalB = s.position(B).balance();

        assertThrows(BusinessRuleViolation.class, () -> s.correctLatestClose(
                original.id(), CloseId.generate(), loss(2_000_000),
                ManagerFault.of(idr(3_000_000), "attribution exceeds corrected loss"),
                ProfitDisposition.DISTRIBUTE, NOW));

        assertEquals(CloseStatus.ACTIVE, s.close(original.id()).status());
        assertEquals(movementCount, s.movements().size());
        assertEquals(0, capitalA.compareTo(s.position(A).balance()));
        assertEquals(0, capitalB.compareTo(s.position(B).balance()));
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void invalidCloseInputsAreRejectedBeforeAnyAccountingMutation() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        int movementCount = s.movements().size();
        int closeCount = s.closes().size();

        assertThrows(NullPointerException.class, () -> s.correctLatestClose(original.id(), null,
                profit(5_000_000), ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW));
        assertThrows(NullPointerException.class, () -> s.correctLatestClose(original.id(), CloseId.generate(),
                profit(5_000_000), ManagerFault.none(), null, NOW));

        assertEquals(movementCount, s.movements().size());
        assertEquals(closeCount, s.closes().size());
        assertEquals(CloseStatus.ACTIVE, s.close(original.id()).status());
        assertTrue(s.verifyIntegrity().isEmpty());

        Syirkah fresh = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        int freshMovementCount = fresh.movements().size();
        assertThrows(NullPointerException.class, () -> fresh.closePeriod(CloseId.generate(), JAN1, JAN31,
                profit(5_000_000), ManagerFault.none(), null, NOW));
        assertEquals(freshMovementCount, fresh.movements().size());
        assertTrue(fresh.closes().isEmpty());
        assertTrue(fresh.verifyIntegrity().isEmpty());
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
    void correctedCloseCanBeCorrectedAgainBeforeItsLiabilityIsSettled() {
        Syirkah s = open(inan(100_000_000, 0, ratio(A, 40, B, 60)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(4_000_000), "repeat-a", NOW);
        s.distributeProfit(B, idr(6_000_000), "repeat-b", NOW);

        CloseSnapshot first = s.correctLatestClose(original.id(), CloseId.generate(), profit(5_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);
        eq(2_000_000, s.position(A).liability());
        eq(3_000_000, s.position(B).liability());

        CloseSnapshot second = s.correctLatestClose(first.id(), CloseId.generate(), profit(12_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);

        assertEquals(CloseStatus.REVERSED, s.close(first.id()).status());
        assertEquals(first.id(), second.supersedes());
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        eq(800_000, s.position(A).payable());
        eq(1_200_000, s.position(B).payable());
        assertTrue(s.reconcile().balanced());
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


    @Test
    void explicitLossWeightsOnExhaustedPartnersRemainEntityDeficit() {
        SyirkahContract contract = new SyirkahContract(1, SyirkahType.ABDAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, BigDecimal.ZERO, B, BigDecimal.ONE), Set.of(), List.of("W1", "W2"), true, JAN1);
        Map<PartnerId, PartnerCapitalState> capital = Map.of(
                A, new PartnerCapitalState(A, idr(10), idr(10)),
                B, new PartnerCapitalState(B, idr(0), idr(0)));

        List<AllocationLine> lines = ProfitLossAllocator.allocate(contract, capital, idr(-5),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, 0);

        eq(0, lines.stream().map(AllocationLine::lossBorne).reduce(idr(0), Money::add));
        eq(0, lines.stream().map(AllocationLine::managerLiability).reduce(idr(0), Money::add));
    }

    // ---------------------------------------------------------------- liquidation

    @Test
    void liquidationIsRejectedWhileEarlierPartnerLiabilityRemainsOpen() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "documented breach"), ProfitDisposition.DISTRIBUTE, NOW);
        s.startLiquidation(NOW);

        assertThrows(BusinessRuleViolation.class, () -> s.liquidate(
                new LiquidationInput(idr(100_000_000), idr(0), true, FEB28),
                ManagerFault.none(), NOW));
        assertEquals(SyirkahStatus.LIQUIDATING, s.status());
        eq(3_000_000, s.position(B).liability());

        s.settleLiability(B, idr(3_000_000), "before-liquidation", NOW);
        LiquidationResult result = s.liquidate(
                new LiquidationInput(idr(100_000_000), idr(0), true, FEB28),
                ManagerFault.none(), NOW);
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        assertTrue(result.hashIsValid());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

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
    void liquidationBeyondCapitalRecordsEntityDeficitNotPartnerDebt() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(0), idr(30_000_000), true, FEB28),
                ManagerFault.none(), NOW);
        // 30M external debt remains unfunded after all 150M partner capital is exhausted.
        eq(30_000_000, r.unallocatedLoss());
        eq(0, r.payouts().get(0).owed());
        eq(0, r.payouts().get(1).owed());
        eq(0, s.position(A).liability());
        eq(0, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void liquidationManagerFaultLiabilityIsSeparateFromUnfundedEntityDeficit() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.startLiquidation(NOW);

        // No assets remain against 150M external debt and 100M capital claim.
        // The manager is liable for 20M of the loss; the remaining 130M is still unfunded.
        LiquidationResult result = s.liquidate(
                new LiquidationInput(idr(0), idr(150_000_000), true, FEB28),
                ManagerFault.of(idr(20_000_000), "documented breach"), NOW);

        eq(-250_000_000, result.netResult());
        eq(20_000_000, result.payouts().stream()
                .filter(p -> p.partner().equals(B)).findFirst().orElseThrow().owed());
        eq(130_000_000, result.unallocatedLoss());
        eq(0, s.position(A).balance());
        eq(20_000_000, s.position(B).liability());
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
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
    void profitDistributionIsFrozenAfterLiquidationStarts() {
        Syirkah s = open(inan(100_000, 100_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000), ProfitDisposition.DISTRIBUTE);
        s.startLiquidation(NOW);

        assertThrows(tech.kayys.numou.foundation.domain.exception.InvalidStateException.class,
                () -> s.distributeProfit(A, idr(1_000), "after-liquidation-start", NOW));
    }

    @Test
    void liquidationRejectsManagerFaultLiabilityInWrongCurrency() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.startLiquidation(NOW);
        var usd = tech.kayys.numou.foundation.domain.valueobject.Currency.of("USD");
        Money wrongCurrency = Money.of(BigDecimal.ONE, usd);

        assertThrows(BusinessRuleViolation.class, () -> s.liquidate(
                new LiquidationInput(idr(0), idr(0), true, FEB28),
                ManagerFault.of(wrongCurrency, "documented breach"), NOW));
    }


    @Test
    void startingLiquidationRejectsNullTimestampWithoutChangingAggregate() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        List<PartnerMovement> before = List.copyOf(s.movements());
        String beforeIntegrity = s.movements().isEmpty() ? AuditHash.GENESIS
                : s.movements().get(s.movements().size() - 1).hash();

        assertThrows(NullPointerException.class, () -> s.startLiquidation(null));

        assertEquals(SyirkahStatus.ACTIVE, s.status());
        assertEquals(before, s.movements());
        assertEquals(beforeIntegrity, s.movements().isEmpty() ? AuditHash.GENESIS
                : s.movements().get(s.movements().size() - 1).hash());
        assertNull(s.liquidation());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void liquidationRejectsNullInputsBeforeChangingStateOrAudit() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        List<PartnerMovement> movementsBefore = List.copyOf(s.movements());
        List<String> integrityBefore = s.verifyIntegrity();

        assertThrows(NullPointerException.class, () -> s.liquidate(null, ManagerFault.none(), NOW));
        assertThrows(NullPointerException.class, () -> s.liquidate(
                new LiquidationInput(idr(200_000_000), idr(0), true, FEB28), ManagerFault.none(), null));

        assertEquals(SyirkahStatus.LIQUIDATING, s.status());
        assertEquals(movementsBefore, s.movements());
        assertNull(s.liquidation());
        assertEquals(integrityBefore, s.verifyIntegrity());
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void terminatedAggregateRejectsLiabilitySettlementWithoutBreakingLiquidationAudit() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.startLiquidation(NOW);
        s.liquidate(new LiquidationInput(idr(0), idr(150_000_000), true, FEB28),
                ManagerFault.of(idr(20_000_000), "documented breach"), NOW);

        assertTrue(s.position(B).liability().isPositive(),
                "liquidation may report a manager-fault receivable to recover outside the terminated aggregate");
        assertThrows(tech.kayys.numou.foundation.domain.exception.InvalidStateException.class,
                () -> s.settleLiability(B, idr(1), "late-recovery", NOW));
        assertTrue(s.verifyIntegrity().isEmpty(),
                "a rejected post-termination settlement must preserve the liquidation audit hash");
    }

    @Test
    void liquidationAuditHashBindsPayoutsAndAllocationLines() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult result = s.liquidate(
                new LiquidationInput(idr(200_000_000), idr(20_000_000), true, FEB28),
                ManagerFault.none(), NOW);

        assertTrue(result.hashIsValid());
        LiquidationResult.Payout original = result.payouts().get(0);
        List<LiquidationResult.Payout> tamperedPayouts = new ArrayList<>(result.payouts());
        tamperedPayouts.set(0, new LiquidationResult.Payout(original.partner(),
                original.cashReceived().add(idr(1)), original.owed()));
        LiquidationResult tampered = new LiquidationResult(result.input(), result.residual(), result.netResult(),
                result.lines(), tamperedPayouts, result.completedAt(), result.hash());

        assertFalse(tampered.hashIsValid(), "changing a liquidation payout must invalidate its audit hash");

        AllocationLine originalLine = result.lines().get(0);
        List<AllocationLine> tamperedLines = new ArrayList<>(result.lines());
        tamperedLines.set(0, new AllocationLine(originalLine.partner(),
                originalLine.capitalRestored().add(idr(1)), originalLine.profitShare(), originalLine.lossBorne(),
                originalLine.managerLiability(), originalLine.retained()));
        LiquidationResult tamperedAllocation = new LiquidationResult(result.input(), result.residual(),
                result.netResult(), tamperedLines, result.payouts(), result.completedAt(), result.hash());
        assertFalse(tamperedAllocation.hashIsValid(),
                "changing a liquidation allocation must invalidate its audit hash");
    }

    @Test
    void snapshotRestorePreservesContractHistoryDepartedPartnersAndPositions() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(5_000_000), "restore-a", NOW);
        s.distributeProfit(B, idr(5_000_000), "restore-b", NOW);
        SyirkahContract next = new SyirkahContract(2, SyirkahType.INAN, IDR,
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true),
                        new Partner(C, "C", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)),
                        CapitalContribution.cash(C, idr(50_000_000))),
                ratio(A, 50, C, 50), ProfitBasis.NET_PROFIT, null, Set.of(),
                List.of("W1", "W2"), true, FEB1);
        s.reviseContract(next, Set.of(A, B, C), NOW);

        Syirkah restored = Syirkah.restore(s.snapshot());

        assertEquals(2, restored.contractHistory().size());
        assertEquals(2, restored.contract().version());
        assertEquals("B", restored.statement(B).name(), "departed partner identity must remain reportable");
        eq(0, restored.position(B).balance());
        eq(0, restored.position(B).basis());
        eq(100_000_000, restored.position(A).balance());
        eq(50_000_000, restored.position(C).balance());
        assertEquals(s.movements(), restored.movements());
        assertEquals(s.closes(), restored.closes());
        assertTrue(restored.verifyIntegrity().isEmpty());
    }

    @Test
    void restoreRejectsClosedSinceRevisionFlagThatContradictsCloseHistory() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), false, snapshot.contracts(), snapshot.movements(), snapshot.closes(),
                snapshot.liquidation(), snapshot.version(), snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("closedSinceRevision flag disagrees with current akad close history"));
    }

    @Test
    void restoreRejectsDuplicateCloseIdsEvenWhenCloseHashesWouldAlsoFail() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        CloseSnapshot replacement = s.correctLatestClose(original.id(), CloseId.generate(), profit(9_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);
        Syirkah.Snapshot snapshot = s.snapshot();
        CloseSnapshot duplicate = new CloseSnapshot(original.id(), replacement.sequence(),
                replacement.periodStart(), replacement.periodEnd(), replacement.contractVersion(),
                replacement.contractCanonical(), replacement.result(), replacement.disposition(), replacement.fault(),
                replacement.lines(), replacement.supersedes(), replacement.status(),
                replacement.firstMovementSequence(), replacement.lastMovementSequence(), replacement.closedAt(),
                replacement.previousHash(), replacement.hash());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                List.of(snapshot.closes().get(0), duplicate), snapshot.liquidation(), snapshot.version(),
                snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("duplicate close id"));
    }

    @Test
    void restoreRejectsCloseCorrectionWithMissingSupersededClose() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.correctLatestClose(original.id(), CloseId.generate(), profit(9_000_000),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE, NOW);
        Syirkah.Snapshot snapshot = s.snapshot();
        CloseSnapshot replacement = snapshot.closes().get(1);
        CloseSnapshot forgedReplacement = new CloseSnapshot(replacement.id(), replacement.sequence(),
                replacement.periodStart(), replacement.periodEnd(), replacement.contractVersion(),
                replacement.contractCanonical(), replacement.result(), replacement.disposition(), replacement.fault(),
                replacement.lines(), CloseId.generate(), replacement.status(),
                replacement.firstMovementSequence(), replacement.lastMovementSequence(), replacement.closedAt(),
                replacement.previousHash(), replacement.hash());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                List.of(snapshot.closes().get(0), forgedReplacement), snapshot.liquidation(), snapshot.version(),
                snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("supersedes a close that is missing"));
    }

    @Test
    void restoreRejectsContractHistoryThatNoLongerMatchesAHashedClose() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();
        SyirkahContract original = snapshot.contracts().get(0);
        SyirkahContract substituted = new SyirkahContract(original.version(), original.type(), original.currency(),
                original.partners(), original.capital(), ratio(A, 45, B, 55), original.profitBasis(),
                original.lossSharing(), original.declaredClauses(), original.witnesses(),
                original.ijabQabulRecorded(), original.effectiveFrom());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), List.of(substituted), snapshot.movements(),
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("contract terms do not match stored contract history"));
    }

    @Test
    void restoreRejectsTamperedLiquidationAuditSnapshot() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        LiquidationResult result = s.liquidate(
                new LiquidationInput(idr(200_000_000), idr(20_000_000), true, FEB28),
                ManagerFault.none(), NOW);

        LiquidationResult.Payout payout = result.payouts().get(0);
        List<LiquidationResult.Payout> tamperedPayouts = new ArrayList<>(result.payouts());
        tamperedPayouts.set(0, new LiquidationResult.Payout(payout.partner(),
                payout.cashReceived().add(idr(1)), payout.owed()));
        LiquidationResult tampered = new LiquidationResult(result.input(), result.residual(), result.netResult(),
                result.lines(), tamperedPayouts, result.completedAt(), result.hash());

        Syirkah.Snapshot snapshot = s.snapshot();
        Syirkah.Snapshot forgedSnapshot = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                snapshot.closes(), tampered, snapshot.version(), snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forgedSnapshot));
        assertTrue(error.getMessage().contains("liquidation hash mismatch"));
    }

    @Test
    void domainEventsAreRaised() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        assertEquals(2, s.pullDomainEvents().size());
        assertEquals(0, s.pullDomainEvents().size());
    }

    @Test
    void idempotentProfitDistributionReplayDoesNotRaiseDuplicateDomainEvent() {
        Syirkah s = open(inan(100, 0, ratio(A, 40, B, 60)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        s.pullDomainEvents();

        s.distributeProfit(A, idr(1), "same-payment", NOW);
        assertEquals(1, s.pullDomainEvents().size());

        s.distributeProfit(A, idr(1), "same-payment", NOW);
        assertTrue(s.pullDomainEvents().isEmpty(),
                "an idempotent replay with the same amount must not emit a second payment event");
        eq(3, s.position(A).payable());
        assertTrue(s.verifyIntegrity().isEmpty());
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

    @Test
    void closeAuditHashBindsManagerFaultFinding() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        CloseSnapshot close = s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(8_000_000),
                ManagerFault.of(idr(3_000_000), "ignored agreed purchase limit"),
                ProfitDisposition.DISTRIBUTE, NOW);

        assertTrue(close.hashIsValid());
        CloseSnapshot tampered = new CloseSnapshot(
                close.id(), close.sequence(), close.periodStart(), close.periodEnd(), close.contractVersion(),
                close.contractCanonical(), close.result(), close.disposition(),
                ManagerFault.of(idr(3_000_000), "different fault finding"), close.lines(), close.supersedes(),
                close.status(), close.firstMovementSequence(), close.lastMovementSequence(), close.closedAt(),
                close.previousHash(), close.hash());

        assertFalse(tampered.hashIsValid(), "changing a material fault finding must invalidate the audit hash");
    }



    @Test
    void accountingScaleMustBeSupportedWhenOpeningAndRestoring() {
        SyirkahContract contract = inan(100, 100, ratio(A, 50, B, 50));

        assertThrows(IllegalArgumentException.class,
                () -> Syirkah.open(SyirkahId.generate(), tech.kayys.numou.foundation.domain.tenant.TenantId.generate(),
                        contract, -1, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> Syirkah.open(SyirkahId.generate(), tech.kayys.numou.foundation.domain.tenant.TenantId.generate(),
                        contract, 19, NOW));

        Syirkah s = open(contract);
        Syirkah.Snapshot snapshot = s.snapshot();
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), 19,
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("unsupported amount scale 19"));
    }

    @Test
    void restoreRejectsMissingSnapshotTimestampsAndNegativeAggregateVersion() {
        Syirkah.Snapshot snapshot = open(inan(100, 100, ratio(A, 50, B, 50))).snapshot();

        Syirkah.Snapshot missingCreatedAt = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.scale(), snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                snapshot.movements(), snapshot.closes(), snapshot.liquidation(), snapshot.version(), null,
                snapshot.updatedAt());
        Syirkah.Snapshot missingUpdatedAt = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.scale(), snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                snapshot.movements(), snapshot.closes(), snapshot.liquidation(), snapshot.version(),
                snapshot.createdAt(), null);
        Syirkah.Snapshot negativeVersion = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.scale(), snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                snapshot.movements(), snapshot.closes(), snapshot.liquidation(), -1, snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException missingCreatedError = assertThrows(IllegalStateException.class,
                () -> Syirkah.restore(missingCreatedAt));
        assertTrue(missingCreatedError.getMessage().contains("missing required state"));
        IllegalStateException missingUpdatedError = assertThrows(IllegalStateException.class,
                () -> Syirkah.restore(missingUpdatedAt));
        assertTrue(missingUpdatedError.getMessage().contains("missing required state"));
        IllegalStateException versionError = assertThrows(IllegalStateException.class,
                () -> Syirkah.restore(negativeVersion));
        assertTrue(versionError.getMessage().contains("negative aggregate version -1"));
    }

    @Test
    void restoreRejectsMovementReferencingUnknownCloseEvenWhenMovementChainIsRehashed() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();
        List<PartnerMovement> forgedMovements = new ArrayList<>();
        String previousHash = AuditHash.GENESIS;
        CloseId unknownClose = CloseId.generate();
        for (PartnerMovement movement : snapshot.movements()) {
            CloseId closeId = movement.sequence() == 1 ? unknownClose : movement.closeId();
            PartnerMovement rebuilt = PartnerMovement.create(movement.sequence(), movement.partner(), movement.type(),
                    movement.amount(), closeId, movement.reference(), movement.occurredAt(), previousHash);
            forgedMovements.add(rebuilt);
            previousHash = rebuilt.hash();
        }
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), forgedMovements,
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("movement references a close absent from close history"));
    }

    @Test
    void restoreRejectsMovementForPartnerAbsentFromContractHistory() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        Syirkah.Snapshot snapshot = s.snapshot();
        PartnerMovement originalFirst = snapshot.movements().get(0);
        PartnerMovement originalSecond = snapshot.movements().get(1);
        PartnerMovement forgedFirst = PartnerMovement.create(1, C, originalFirst.type(), originalFirst.amount(),
                originalFirst.closeId(), originalFirst.reference(), originalFirst.occurredAt(), AuditHash.GENESIS);
        PartnerMovement forgedSecond = PartnerMovement.create(2, originalSecond.partner(), originalSecond.type(),
                originalSecond.amount(), originalSecond.closeId(), originalSecond.reference(),
                originalSecond.occurredAt(), forgedFirst.hash());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                List.of(forgedFirst, forgedSecond), snapshot.closes(), snapshot.liquidation(),
                snapshot.version(), snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("movement references a partner absent from contract history"));
    }

    @Test
    void restoreRejectsCloseMovementRangeTampering() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();

        CloseSnapshot tamperedClose = new CloseSnapshot(original.id(), original.sequence(),
                original.periodStart(), original.periodEnd(), original.contractVersion(),
                original.contractCanonical(), original.result(), original.disposition(), original.fault(),
                original.lines(), original.supersedes(), original.status(),
                original.firstMovementSequence() + 1, original.lastMovementSequence(),
                original.closedAt(), original.previousHash(), original.hash());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                List.of(tamperedClose), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
    }

    @Test
    void restoreRejectsCloseSnapshotWithMissingMetadataOrInvalidPeriod() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();

        CloseSnapshot missingStatus = new CloseSnapshot(original.id(), original.sequence(),
                original.periodStart(), original.periodEnd(), original.contractVersion(),
                original.contractCanonical(), original.result(), original.disposition(), original.fault(),
                original.lines(), original.supersedes(), null, original.firstMovementSequence(),
                original.lastMovementSequence(), original.closedAt(), original.previousHash(), original.hash());
        Syirkah.Snapshot missingStatusSnapshot = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.scale(), snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                snapshot.movements(), List.of(missingStatus), snapshot.liquidation(), snapshot.version(),
                snapshot.createdAt(), snapshot.updatedAt());
        IllegalStateException statusError = assertThrows(IllegalStateException.class,
                () -> Syirkah.restore(missingStatusSnapshot));
        assertTrue(statusError.getMessage().contains("missing required metadata"));

        CloseSnapshot invalidPeriod = new CloseSnapshot(original.id(), original.sequence(),
                original.periodEnd(), original.periodStart(), original.contractVersion(),
                original.contractCanonical(), original.result(), original.disposition(), original.fault(),
                original.lines(), original.supersedes(), original.status(), original.firstMovementSequence(),
                original.lastMovementSequence(), original.closedAt(), original.previousHash(), original.hash());
        Syirkah.Snapshot invalidPeriodSnapshot = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(),
                snapshot.scale(), snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(),
                snapshot.movements(), List.of(invalidPeriod), snapshot.liquidation(), snapshot.version(),
                snapshot.createdAt(), snapshot.updatedAt());
        IllegalStateException periodError = assertThrows(IllegalStateException.class,
                () -> Syirkah.restore(invalidPeriodSnapshot));
        assertTrue(periodError.getMessage().contains("period end before period start"));
    }


    @Test
    void restoreRejectsMovementWithMissingMetadataBeforeHashVerification() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        Syirkah.Snapshot snapshot = s.snapshot();
        PartnerMovement original = snapshot.movements().get(0);
        PartnerMovement missingType = new PartnerMovement(original.id(), original.sequence(), original.partner(),
                null, original.amount(), original.closeId(), original.reference(), original.occurredAt(),
                original.previousHash(), original.hash());
        List<PartnerMovement> forgedMovements = new ArrayList<>(snapshot.movements());
        forgedMovements.set(0, missingType);
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), forgedMovements,
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("movement has missing required metadata"));
    }


    @Test
    void restoreRejectsMovementWithTamperedIdentityEvenWhenHashChainRemainsValid() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        Syirkah.Snapshot snapshot = s.snapshot();
        PartnerMovement original = snapshot.movements().get(0);
        PartnerMovement tampered = new PartnerMovement(java.util.UUID.randomUUID(), original.sequence(),
                original.partner(), original.type(), original.amount(), original.closeId(), original.reference(),
                original.occurredAt(), original.previousHash(), original.hash());
        List<PartnerMovement> movements = new ArrayList<>(snapshot.movements());
        movements.set(0, tampered);
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), movements,
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("id does not match its canonical identity"));
    }


    @Test
    void restoreRejectsSnapshotUpdatedBeforeCreation() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        Syirkah.Snapshot snapshot = s.snapshot();
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("updatedAt precedes createdAt"));
    }


    @Test
    void restoreRejectsMovementAmountWithExcessAccountingPrecision() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        Syirkah.Snapshot snapshot = s.snapshot();
        PartnerMovement original = snapshot.movements().get(0);
        Money excessivePrecision = Money.of(new BigDecimal("100.001"), original.amount().currency());
        PartnerMovement tampered = PartnerMovement.create(original.sequence(), original.partner(), original.type(),
                excessivePrecision, original.closeId(), original.reference(), original.occurredAt(),
                original.previousHash());
        List<PartnerMovement> movements = new ArrayList<>(snapshot.movements());
        movements.set(0, tampered);
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), movements,
                snapshot.closes(), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("exceeds the aggregate accounting scale"));
    }


    @Test
    void restoreRejectsCloseAmountsWithExcessAccountingPrecisionEvenWhenRehashed() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();
        FinancialResult alteredResult = FinancialResult.of(
                Money.of(new BigDecimal("10.001"), IDR), original.result().directCosts(),
                original.result().operatingCosts(), original.result().otherCosts());
        String alteredHash = CloseSnapshot.computeHashV3(original.id(), original.sequence(),
                original.periodStart(), original.periodEnd(), original.contractVersion(),
                original.contractCanonical(), alteredResult, original.disposition(), original.fault(),
                original.lines(), original.supersedes(), original.previousHash(),
                original.firstMovementSequence(), original.lastMovementSequence());
        CloseSnapshot altered = new CloseSnapshot(original.id(), original.sequence(), original.periodStart(),
                original.periodEnd(), original.contractVersion(), original.contractCanonical(), alteredResult,
                original.disposition(), original.fault(), original.lines(), original.supersedes(), original.status(),
                original.firstMovementSequence(), original.lastMovementSequence(), original.closedAt(),
                original.previousHash(), alteredHash);
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                List.of(altered), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("amount exceeds the aggregate accounting scale"));
    }

    @Test
    void restoreRejectsCloseWithDuplicateAllocationPartnerEvenWhenRehashed() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        CloseSnapshot original = close(s, JAN1, JAN31, profit(10), ProfitDisposition.DISTRIBUTE);
        Syirkah.Snapshot snapshot = s.snapshot();
        List<AllocationLine> duplicateLines = new ArrayList<>(original.lines());
        duplicateLines.set(1, duplicateLines.get(0));
        String alteredHash = CloseSnapshot.computeHashV3(original.id(), original.sequence(),
                original.periodStart(), original.periodEnd(), original.contractVersion(),
                original.contractCanonical(), original.result(), original.disposition(), original.fault(),
                duplicateLines, original.supersedes(), original.previousHash(),
                original.firstMovementSequence(), original.lastMovementSequence());
        CloseSnapshot altered = new CloseSnapshot(original.id(), original.sequence(), original.periodStart(),
                original.periodEnd(), original.contractVersion(), original.contractCanonical(), original.result(),
                original.disposition(), original.fault(), duplicateLines, original.supersedes(), original.status(),
                original.firstMovementSequence(), original.lastMovementSequence(), original.closedAt(),
                original.previousHash(), alteredHash);
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                List.of(altered), snapshot.liquidation(), snapshot.version(), snapshot.createdAt(),
                snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("duplicate allocation lines for partner"));
    }

    @Test
    void restoreRejectsLiquidationAmountsWithExcessAccountingPrecision() {
        Syirkah s = open(inan(100, 100, ratio(A, 50, B, 50)));
        s.startLiquidation(Instant.parse("2026-02-01T00:00:00Z"));
        s.liquidate(new LiquidationInput(Money.of(new BigDecimal("200.00"), IDR),
                Money.of(new BigDecimal("0.00"), IDR), true, LocalDate.parse("2026-02-01")),
                ManagerFault.none(), Instant.parse("2026-02-01T01:00:00Z"));
        Syirkah.Snapshot snapshot = s.snapshot();
        LiquidationResult original = snapshot.liquidation();
        LiquidationResult forgedResult = new LiquidationResult(original.input(),
                Money.of(new BigDecimal("200.001"), IDR), original.netResult(), original.lines(),
                original.payouts(), original.completedAt(), original.hash());
        Syirkah.Snapshot forged = new Syirkah.Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.scale(),
                snapshot.status(), snapshot.closedSinceRevision(), snapshot.contracts(), snapshot.movements(),
                snapshot.closes(), forgedResult, snapshot.version(), snapshot.createdAt(), snapshot.updatedAt());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> Syirkah.restore(forged));
        assertTrue(error.getMessage().contains("exceeds the aggregate accounting scale"));
    }

}
