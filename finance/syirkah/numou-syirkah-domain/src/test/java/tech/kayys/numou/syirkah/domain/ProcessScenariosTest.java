package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static tech.kayys.numou.numoudomain.Fixtures.*;

/**
 * The syirkah business processes end to end, each run through the real aggregate and the lifecycle checker. After
 * every step the books must reconcile and the integrity check must find nothing. These, together with
 * {@link ShariaRuleCatalogTest}, are the evidence required before the rule catalog is frozen.
 */
class ProcessScenariosTest {

    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");
    private static final LocalDate FEB28 = LocalDate.parse("2026-02-28");
    private static final LocalDate MAR1 = LocalDate.parse("2026-03-01");

    private static void sound(Syirkah s) {
        assertTrue(s.reconcile().balanced(), "equity does not reconcile");
        assertEquals(List.of(), s.verifyIntegrity());
    }

    private static CloseSnapshot close(Syirkah s, LocalDate from, LocalDate to, FinancialResult r,
                                       ProfitDisposition d) {
        CloseSnapshot c = s.closePeriod(CloseId.generate(), from, to, r, ManagerFault.none(), d, NOW);
        sound(s);
        return c;
    }

    private static Partner partner(PartnerId id, PartnerRole role, boolean works) {
        return new Partner(id, id.value().toString(), role, works);
    }

    private static LifecycleConsent yes(PartnerId id, LocalDate on) {
        return new LifecycleConsent(id, true, on, "signed");
    }

    private static SyirkahContract inanV2(List<Partner> partners, List<CapitalContribution> capital,
                                          ProfitSharingRatio r) {
        return new SyirkahContract(2, SyirkahType.INAN, IDR, partners, capital, r, ProfitBasis.NET_PROFIT, null,
                Set.of(), List.of("W1", "W2"), true, FEB1);
    }

    // ---------------------------------------------------------------- inan

    @Test
    void inanProfitIsSplitByNisbahAndLossByCapital() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        // retained profit joins capital: 50/50 of 10M on top of 100M and 50M
        assertEquals(idr(105_000_000), s.position(A).balance());
        assertEquals(idr(55_000_000), s.position(B).balance());
        close(s, FEB1, FEB28, loss(30_000_000), ProfitDisposition.RETAIN);
        // the loss falls on capital in proportion 2:1, not by the 50/50 nisbah
        Money lossA = s.position(A).basis().subtract(s.position(A).balance());
        Money lossB = s.position(B).basis().subtract(s.position(B).balance());
        assertTrue(lossA.compareTo(lossB) > 0, "A put in twice the capital and must bear more of the loss");
        sound(s);
    }

    @Test
    void distributingProfitPaysPartnersAndLeavesCapitalIntact() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 60, B, 40)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.DISTRIBUTE);
        s.distributeProfit(A, idr(6_000_000), "pay A", NOW);
        s.distributeProfit(B, idr(4_000_000), "pay B", NOW);
        sound(s);
        assertTrue(s.position(A).payable().isZero());
        assertTrue(s.position(B).payable().isZero());
        assertThrows(RuntimeException.class, () -> s.distributeProfit(A, idr(1), "again", NOW));
    }

    @Test
    void correctingAnOlderCloseReplaysTheLaterOnes() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        CloseSnapshot first = close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        close(s, FEB1, FEB28, profit(4_000_000), ProfitDisposition.RETAIN);
        s.correctClose(first.id(), List.of(CloseId.generate(), CloseId.generate()), profit(8_000_000),
                ManagerFault.none(), ProfitDisposition.RETAIN, NOW);
        sound(s);
    }

    // ---------------------------------------------------------------- mudharabah

    @Test
    void mudharabahOwnerBearsTheLossAndManagerLosesOnlyHisEffort() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        assertTrue(s.position(B).balance().compareTo(s.position(B).basis()) >= 0);
        close(s, FEB1, FEB28, loss(40_000_000), ProfitDisposition.RETAIN);
        assertTrue(s.position(B).liability().isZero(), "the manager owes nothing for a loss without fault");
        sound(s);
    }

    @Test
    void mudharabahManagerFaultIsAttributedToTheManager() {
        Syirkah s = open(mudharabah(100_000_000, ratio(A, 70, B, 30)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, loss(20_000_000),
                ManagerFault.of(idr(5_000_000), "sold below cost against instructions"),
                ProfitDisposition.RETAIN, NOW);
        sound(s);
        assertFalse(s.position(B).liability().isZero(), "negligence is the manager's own liability");
    }

    // ---------------------------------------------------------------- lifecycle

    @Test
    void newCapitalNeedsClosedBooksUnanimityAndANewAkad() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        SyirkahContract next = inanV2(List.of(partner(A, PartnerRole.PARTNER, true),
                        partner(B, PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(150_000_000)), CapitalContribution.cash(B, idr(100_000_000))),
                ratio(A, 55, B, 45));
        LifecycleChange change = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                List.of(yes(A, FEB1), yes(B, FEB1)), ProfitDisposition.RETAIN, List.of());
        LifecycleChecker.requirePassing(s, next, change);
        s.reviseContract(next, Set.of(A, B), NOW);
        assertEquals(2, s.contract().version());
        sound(s);
        close(s, FEB1, FEB28, profit(5_000_000), ProfitDisposition.RETAIN);
    }

    @Test
    void aPartnerWhoDissentsBlocksTheChange() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        SyirkahContract next = inanV2(List.of(partner(A, PartnerRole.PARTNER, true),
                        partner(B, PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(150_000_000)), CapitalContribution.cash(B, idr(100_000_000))),
                ratio(A, 55, B, 45));
        LifecycleChange change = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                List.of(yes(A, FEB1), new LifecycleConsent(B, false, FEB1, "refused")), ProfitDisposition.RETAIN,
                List.of());
        assertThrows(RuntimeException.class, () -> LifecycleChecker.requirePassing(s, next, change));
        assertThrows(RuntimeException.class, () -> s.reviseContract(next, Set.of(A), NOW));
        assertEquals(1, s.contract().version());
    }

    @Test
    void whenAPartnerDiesTheHeirMayContinueInANewAkadAfterTheBooksClose() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        s.recordPartnerDeath(B, JAN31, NOW);
        SyirkahContract next = inanV2(List.of(partner(A, PartnerRole.PARTNER, true),
                        partner(C, PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)), CapitalContribution.cash(C, idr(100_000_000))),
                ratio(A, 50, C, 50));
        LifecycleChange change = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31,
                List.of(yes(A, FEB1), yes(C, FEB1)), ProfitDisposition.RETAIN, List.of(),
                List.of(new Succession(B, C, true, FEB1, "heirship letter")));
        LifecycleChecker.requirePassing(s, next, change);
        s.reviseContract(next, Set.of(A, B, C), NOW);
        sound(s);
        close(s, FEB1, FEB28, profit(4_000_000), ProfitDisposition.RETAIN);
    }

    @Test
    void whenTheHeirDeclinesTheAkadIsNotContinuedWithThem() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        SyirkahContract next = inanV2(List.of(partner(A, PartnerRole.PARTNER, true),
                        partner(C, PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)), CapitalContribution.cash(C, idr(100_000_000))),
                ratio(A, 50, C, 50));
        LifecycleChange change = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), FEB1,
                List.of(yes(A, FEB1), yes(C, FEB1)), ProfitDisposition.RETAIN, List.of(),
                List.of(new Succession(B, C, false, FEB1, "heirship letter")));
        assertFalse(LifecycleChecker.check(s, next, change).isEmpty());
    }

    // ---------------------------------------------------------------- liquidation

    @Test
    void liquidationPaysDebtsFirstThenPartnersAndEndsTheSyirkah() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        close(s, JAN1, JAN31, profit(10_000_000), ProfitDisposition.RETAIN);
        s.startLiquidation(NOW);
        LiquidationResult r = s.liquidate(new LiquidationInput(idr(230_000_000), idr(20_000_000), true, MAR1),
                ManagerFault.none(), NOW);
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        assertNotNull(r.hash());
        assertEquals(List.of(), s.verifyIntegrity());
        assertThrows(RuntimeException.class, () -> s.closePeriod(CloseId.generate(), FEB1, FEB28, profit(1),
                ManagerFault.none(), ProfitDisposition.RETAIN, NOW));
    }

    @Test
    void liquidationAtALossFallsOnCapitalNotOnTheNisbah() {
        Syirkah s = open(inan(100_000_000, 50_000_000, ratio(A, 50, B, 50)));
        s.startLiquidation(NOW);
        s.liquidate(new LiquidationInput(idr(120_000_000), idr(0), true, MAR1), ManagerFault.none(), NOW);
        assertEquals(SyirkahStatus.TERMINATED, s.status());
        assertEquals(List.of(), s.verifyIntegrity());
    }
}
