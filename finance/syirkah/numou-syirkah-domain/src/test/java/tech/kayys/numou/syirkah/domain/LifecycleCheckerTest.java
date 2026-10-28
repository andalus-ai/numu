package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.*;

class LifecycleCheckerTest {

    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");

    private static Syirkah closed() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, profit(1_000_000), ManagerFault.none(),
                ProfitDisposition.RETAIN, NOW);
        return s;
    }

    private static LifecycleConsent yes(PartnerId p) {
        return new LifecycleConsent(p, true, FEB1, "sig-" + p.value());
    }

    private static SyirkahContract v2(Syirkah s, List<Partner> partners, List<CapitalContribution> cap,
                                      ProfitSharingRatio r) {
        return new SyirkahContract(2, SyirkahType.INAN, IDR, partners, cap, r, ProfitBasis.NET_PROFIT, null,
                Set.of(), List.of("W1", "W2"), true, FEB1);
    }

    private static Partner p(PartnerId id) {
        return new Partner(id, id.value(), PartnerRole.PARTNER, true);
    }

    private static List<String> codes(List<LifecycleFinding> f) {
        return f.stream().map(LifecycleFinding::code).toList();
    }

    @Test
    void newCapitalNeedsEveryConsentAndProfitDecision() {
        Syirkah s = closed();
        SyirkahContract next = v2(s, s.contract().partners(),
                List.of(CapitalContribution.cash(A, idr(150_000_000)), CapitalContribution.cash(B, idr(100_000_000))),
                ratio(A, 50, B, 50));
        LifecycleChange bad = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1, List.of(yes(A)), null,
                List.of());
        List<String> c = codes(LifecycleChecker.check(s, next, bad));
        assertTrue(c.contains("CONSENT_MISSING"));
        assertTrue(c.contains("PROFIT_DECISION_MISSING"));
        LifecycleChange ok = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1, List.of(yes(A), yes(B)),
                ProfitDisposition.RETAIN, List.of());
        assertEquals(List.of(), LifecycleChecker.check(s, next, ok));
    }

    @Test
    void dissentBlocksAndIsReported() {
        Syirkah s = closed();
        SyirkahContract next = v2(s, s.contract().partners(),
                List.of(CapitalContribution.cash(A, idr(150_000_000)), CapitalContribution.cash(B, idr(100_000_000))),
                ratio(A, 50, B, 50));
        LifecycleChange ch = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                List.of(yes(A), new LifecycleConsent(B, false, FEB1, "refused")), ProfitDisposition.RETAIN, List.of());
        assertTrue(codes(LifecycleChecker.check(s, next, ch)).contains("DISSENT"));
    }

    @Test
    void booksMustBeClosedAndVersionMustAdvance() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        SyirkahContract same = s.contract();
        LifecycleChange ch = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1, List.of(yes(A), yes(B)),
                ProfitDisposition.RETAIN, List.of());
        List<String> c = codes(LifecycleChecker.check(s, same, ch));
        assertTrue(c.contains("BOOKS_NOT_CLOSED"));
        assertTrue(c.contains("NEW_AKAD_VERSION"));
    }

    @Test
    void newCapitalMayNotReduceCapital() {
        Syirkah s = closed();
        SyirkahContract next = v2(s, s.contract().partners(),
                List.of(CapitalContribution.cash(A, idr(50_000_000)), CapitalContribution.cash(B, idr(100_000_000))),
                ratio(A, 50, B, 50));
        LifecycleChange ch = new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1, List.of(yes(A), yes(B)),
                ProfitDisposition.RETAIN, List.of());
        assertTrue(codes(LifecycleChecker.check(s, next, ch)).contains("CAPITAL_REDUCED"));
    }

    @Test
    void replacementToOutsiderNeedsSyufahWaiverFromRemainingPartners() {
        Syirkah s = closed();
        SyirkahContract next = v2(s, List.of(p(A), p(C)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)), CapitalContribution.cash(C, idr(100_000_000))),
                ratio(A, 50, C, 50));
        LifecycleChange none = new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(B), FEB1,
                List.of(yes(A), yes(B), yes(C)), ProfitDisposition.RETAIN, List.of());
        assertTrue(codes(LifecycleChecker.check(s, next, none)).contains("SYUFAH_NOT_OFFERED"));
        LifecycleChange exercised = new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(B), FEB1,
                List.of(yes(A), yes(B), yes(C)), ProfitDisposition.RETAIN,
                List.of(new SyufahResponse(A, true, FEB1)));
        assertTrue(codes(LifecycleChecker.check(s, next, exercised)).contains("SYUFAH_EXERCISED"));
        LifecycleChange waived = new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(B), FEB1,
                List.of(yes(A), yes(B), yes(C)), ProfitDisposition.RETAIN,
                List.of(new SyufahResponse(A, false, FEB1)));
        assertEquals(List.of(), LifecycleChecker.check(s, next, waived));
        // and the aggregate accepts the same revision
        s.reviseContract(next, waived.approvals(), NOW);
        assertEquals(2, s.contract().version());
    }

    @Test
    void deathEndsTheAkadAndSurvivorsMustAgreeToANewOne() {
        Syirkah s = closed();
        SyirkahContract stillHasB = v2(s, s.contract().partners(), s.contract().capital(), ratio(A, 50, B, 50));
        LifecycleChange ch = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), FEB1, List.of(yes(A)), null,
                List.of());
        assertTrue(codes(LifecycleChecker.check(s, stillHasB, ch)).contains("DECEASED_REMAINS"));
        SyirkahContract withC = v2(s, List.of(p(A), p(C)),
                List.of(CapitalContribution.cash(A, idr(100_000_000)), CapitalContribution.cash(C, idr(100_000_000))),
                ratio(A, 50, C, 50));
        assertTrue(codes(LifecycleChecker.check(s, withC, ch)).contains("CONSENT_MISSING")); // C has not agreed
        LifecycleChange full = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), FEB1,
                List.of(yes(A), yes(C)), null, List.of());
        assertEquals(List.of(), LifecycleChecker.check(s, withC, full));
    }

    @Test
    void requirePassingThrowsWithAllFindings() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        LifecycleChange ch = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(), FEB1, List.of(), null, List.of());
        assertThrows(BusinessRuleViolation.class, () -> LifecycleChecker.requirePassing(s, s.contract(), ch));
    }
}
