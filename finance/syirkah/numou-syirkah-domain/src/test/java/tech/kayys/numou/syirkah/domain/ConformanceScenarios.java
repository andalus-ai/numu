package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static tech.kayys.numou.numoudomain.Fixtures.*;

/**
 * Deliberately defective inputs for every checker, so the conformance test can see which codes the code really
 * emits. Plain Java, no test framework, so it can also be run on its own.
 */
final class ConformanceScenarios {

    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");

    private ConformanceScenarios() {
    }

    /** Every code any checker emitted across all scenarios. */
    static Set<String> observedCodes() {
        Set<String> codes = new TreeSet<>();
        contractScenarios(codes);
        formationScenarios(codes);
        lifecycleScenarios(codes);
        staffScenarios(codes);
        agricultureScenarios(codes);
        return codes;
    }

    // ------------------------------------------------------------------ contract (rules 1, 2, 4, 5, 6, 7, 8)

    private static Partner p(PartnerId id, PartnerRole role, boolean work) {
        return new Partner(id, id.value().toString(), role, work);
    }

    private static SyirkahContract contract(SyirkahType type, List<Partner> partners, List<CapitalContribution> cap,
                                            ProfitSharingRatio nisbah, ProfitBasis basis,
                                            Map<PartnerId, BigDecimal> loss, Set<ForbiddenClause> clauses,
                                            List<String> witnesses, boolean ijab) {
        return new SyirkahContract(1, type, IDR, partners, cap, nisbah, basis, loss, clauses, witnesses, ijab, JAN1);
    }

    private static void add(Set<String> codes, SyirkahContract c) {
        for (ContractViolation v : c.validate()) {
            codes.add(v.code().startsWith("FORBIDDEN_") ? "FORBIDDEN_" + v.code().substring(10) : v.code());
        }
    }

    private static void contractScenarios(Set<String> codes) {
        List<String> w2 = List.of("W1", "W2");
        Partner a = p(A, PartnerRole.PARTNER, true);
        Partner b = p(B, PartnerRole.PARTNER, true);
        List<CapitalContribution> capA = List.of(CapitalContribution.cash(A, idr(100)));

        // one partner only
        add(codes, contract(SyirkahType.INAN, List.of(a), capA, ratio(A, 100), ProfitBasis.NET_PROFIT, null, Set.of(),
                w2, true));
        // the same partner twice
        add(codes, contract(SyirkahType.INAN, List.of(a, a), capA, ratio(A, 100), ProfitBasis.NET_PROFIT, null,
                Set.of(), w2, true));
        // many things wrong at once: revenue basis, nisbah misses B, every forbidden clause, one witness, no ijab,
        // goods without valuation, capital from a stranger, a partner who gives nothing
        add(codes, contract(SyirkahType.INAN, List.of(a, p(B, PartnerRole.PARTNER, false)),
                List.of(CapitalContribution.cash(A, idr(100)), CapitalContribution.cash(C, idr(5)),
                        new CapitalContribution(A, CapitalKind.GOODS_VALUED_AT_AKAD, idr(5), null)),
                ratio(A, 100), ProfitBasis.REVENUE, null, EnumSet.allOf(ForbiddenClause.class), List.of("W1"), false));
        // inan with no capital
        add(codes, contract(SyirkahType.INAN, List.of(a, b), List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                null, Set.of(), w2, true));
        // mufawadhah: unequal capital, and equal capital but one partner does not work
        add(codes, contract(SyirkahType.MUFAWADHAH, List.of(a, b),
                List.of(CapitalContribution.cash(A, idr(100)), CapitalContribution.cash(B, idr(50))),
                ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), w2, true));
        add(codes, contract(SyirkahType.MUFAWADHAH, List.of(a, p(B, PartnerRole.PARTNER, false)),
                List.of(CapitalContribution.cash(A, idr(100)), CapitalContribution.cash(B, idr(100))),
                ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), w2, true));
        // mudharabah: no roles; a manager with capital who does not work and a provider without capital
        add(codes, contract(SyirkahType.MUDHARABAH, List.of(a, b), capA, ratio(A, 50, B, 50),
                ProfitBasis.NET_PROFIT, null, Set.of(), w2, true));
        add(codes, contract(SyirkahType.MUDHARABAH,
                List.of(p(A, PartnerRole.MANAGER, false), p(B, PartnerRole.CAPITAL_PROVIDER, false)), capA,
                ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), w2, true));
        // abdan with cash capital, a partner who does not work, a role other than partner
        add(codes, contract(SyirkahType.ABDAN, List.of(p(A, PartnerRole.MANAGER, true), p(B, PartnerRole.PARTNER, false)),
                capA, ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT, null, Set.of(), w2, true));
        // loss sharing: explicit in inan; invalid in abdan; not summing to 100% in abdan
        add(codes, contract(SyirkahType.INAN, List.of(a, b), capA, ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, BigDecimal.ONE), Set.of(), w2, true));
        add(codes, contract(SyirkahType.ABDAN, List.of(a, b), List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, new BigDecimal("0.5"), C, new BigDecimal("0.5")), Set.of(), w2, true));
        add(codes, contract(SyirkahType.ABDAN, List.of(a, b), List.of(), ratio(A, 50, B, 50), ProfitBasis.NET_PROFIT,
                Map.of(A, new BigDecimal("0.4"), B, new BigDecimal("0.4")), Set.of(), w2, true));
    }

    // ------------------------------------------------------------------ formation (rules 5, 6, 14 to 19)

    private static PartyRecord party(PartnerId id, LocalDate dob) {
        return new PartyRecord(id, dob, "Jakarta", true, true, true, "I accept");
    }

    private static AkadFormation formation(List<PartyRecord> parties, String activity,
                                           AkadFormation.LawfulnessReview review, Set<String> combined,
                                           List<String> ambiguities, boolean sameSession, boolean withdrawn,
                                           Set<AkadSection> sections, String place, LocalDate signed,
                                           List<Witness> witnesses) {
        return new AkadFormation(parties, activity, review, combined, ambiguities, sameSession, withdrawn, sections,
                place, signed, witnesses);
    }

    private static void formationScenarios(Set<String> codes) {
        SyirkahContract c = inan(100_000_000, 0, ratio(A, 40, B, 60));
        AkadFormation.LawfulnessReview ok = new AkadFormation.LawfulnessReview("Adviser", JAN1, true);
        // everything missing or wrong at once
        AkadFormation bad = formation(
                List.of(new PartyRecord(A, null, " ", false, false, false, ""), party(A, JAN1), party(C, JAN1)),
                " ", null, Set.of("sale and loan"), List.of("unclear price"), false, true, EnumSet.noneOf(AkadSection.class),
                "", null, List.of(new Witness("", WitnessSex.MALE)));
        collect(codes, c, bad, FormationPolicy.bookDefault());
        // birth date after signing; below the minimum age; not lawful; plain witness count
        AkadFormation good = formation(List.of(party(A, JAN1.plusYears(5)), party(B, JAN1.minusYears(10))),
                "Retail", new AkadFormation.LawfulnessReview("Adviser", JAN1, false), Set.of(), List.of(), true, false,
                EnumSet.allOf(AkadSection.class), "Jakarta", JAN1,
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE)));
        collect(codes, c, good, new FormationPolicy(WitnessRule.COUNT_ONLY, true, 18));
        collect(codes, c, good, FormationPolicy.bookDefault());
        // a barred agriculture akad named as the activity
        collect(codes, c, formation(List.of(party(A, JAN1.minusYears(30)), party(B, JAN1.minusYears(30))),
                "Muzara'ah of rice fields", ok, Set.of(), List.of(), true, false, EnumSet.allOf(AkadSection.class),
                "Jakarta", JAN1, List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE))),
                FormationPolicy.bookDefault());
    }

    private static void collect(Set<String> codes, SyirkahContract c, AkadFormation f, FormationPolicy policy) {
        for (FormationFinding x : AkadFormationChecker.check(c, f, policy)) {
            if (!x.code().startsWith("CONTRACT_")) {
                codes.add(x.code());
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle (rules 9, 20, 23, 24, 25)

    private static Syirkah closed() {
        Syirkah s = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        s.closePeriod(CloseId.generate(), JAN1, JAN31, profit(1_000_000), ManagerFault.none(),
                ProfitDisposition.RETAIN, NOW);
        return s;
    }

    private static SyirkahContract next(int version, List<Partner> partners, List<CapitalContribution> cap,
                                        ProfitSharingRatio r) {
        return new SyirkahContract(version, SyirkahType.INAN, IDR, partners, cap, r, ProfitBasis.NET_PROFIT, null,
                Set.of(), List.of("W1", "W2"), true, FEB1);
    }

    private static LifecycleConsent yes(PartnerId id) {
        return new LifecycleConsent(id, true, FEB1, "sig");
    }

    private static void life(Set<String> codes, Syirkah s, SyirkahContract n, LifecycleChange ch) {
        for (LifecycleFinding f : LifecycleChecker.check(s, n, ch)) {
            codes.add(f.code());
        }
    }

    private static void lifecycleScenarios(Set<String> codes) {
        Partner a = p(A, PartnerRole.PARTNER, true);
        Partner b = p(B, PartnerRole.PARTNER, true);
        Partner c = p(C, PartnerRole.PARTNER, true);
        Syirkah s = closed();
        List<CapitalContribution> both = List.of(CapitalContribution.cash(A, idr(100_000_000)),
                CapitalContribution.cash(B, idr(100_000_000)));
        List<CapitalContribution> aAndC = List.of(CapitalContribution.cash(A, idr(100_000_000)),
                CapitalContribution.cash(C, idr(100_000_000)));

        // new capital that removes B, cuts A's capital, adds nothing; no consents but one dissent; no profit decision
        life(codes, s, next(2, List.of(a), List.of(CapitalContribution.cash(A, idr(50_000_000))), ratio(A, 100)),
                new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                        List.of(new LifecycleConsent(A, false, FEB1, "refused")), null, List.of()));
        // books not closed and the version does not advance
        Syirkah fresh = open(inan(100_000_000, 100_000_000, ratio(A, 50, B, 50)));
        life(codes, fresh, fresh.contract(), new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1,
                List.of(yes(A), yes(B)), ProfitDisposition.RETAIN, List.of()));
        // not active
        Syirkah liquidating = closed();
        liquidating.startLiquidation(NOW);
        life(codes, liquidating, next(2, List.of(a, b), both, ratio(A, 50, B, 50)),
                new LifecycleChange(LifecycleKind.NEW_CAPITAL, Set.of(), FEB1, List.of(yes(A), yes(B)),
                        ProfitDisposition.RETAIN, List.of()));
        // replacement: nobody named; a stranger named; the leaver stays while an outsider joins and no one was asked
        life(codes, s, next(2, List.of(a, c), aAndC, ratio(A, 50, C, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(), FEB1, List.of(yes(A), yes(B), yes(C)),
                        ProfitDisposition.RETAIN, List.of()));
        life(codes, s, next(2, List.of(a, b), both, ratio(A, 50, B, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(C), FEB1, List.of(yes(A), yes(B)),
                        ProfitDisposition.RETAIN, List.of()));
        life(codes, s, next(2, List.of(a, b, c), List.of(CapitalContribution.cash(A, idr(100_000_000)),
                        CapitalContribution.cash(B, idr(100_000_000)), CapitalContribution.cash(C, idr(100_000_000))),
                        ratio(A, 40, B, 30, C, 30)),
                new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(B), FEB1, List.of(yes(A), yes(B), yes(C)),
                        ProfitDisposition.RETAIN, List.of()));
        life(codes, s, next(2, List.of(a, c), aAndC, ratio(A, 50, C, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_REPLACEMENT, Set.of(B), FEB1, List.of(yes(A), yes(B), yes(C)),
                        ProfitDisposition.RETAIN, List.of(new SyufahResponse(A, true, FEB1))));
        // death: nobody named; the deceased stays; no heir decision; heir decision for a living partner; heir
        // continues but is not in the akad; heir declined but is in the akad; death after the books closed to
        life(codes, s, next(2, List.of(a, c), aAndC, ratio(A, 50, C, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(), FEB1, List.of(yes(A), yes(C)), null,
                        List.of()));
        life(codes, s, next(2, List.of(a, b), both, ratio(A, 50, B, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31, List.of(yes(A)), null, List.of()));
        life(codes, s, next(2, List.of(a, c), aAndC, ratio(A, 50, C, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), FEB1, List.of(yes(A), yes(C)), null,
                        List.of(), List.of(new Succession(A, C, true, FEB1, "x"))));
        life(codes, s, next(2, List.of(a), List.of(CapitalContribution.cash(A, idr(100_000_000))), ratio(A, 100)),
                new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31, List.of(yes(A)), null, List.of(),
                        List.of(new Succession(B, C, true, FEB1, "heirship"))));
        life(codes, s, next(2, List.of(a, c), aAndC, ratio(A, 50, C, 50)),
                new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31, List.of(yes(A), yes(C)), null,
                        List.of(), List.of(new Succession(B, C, false, FEB1, "heirship"))));
    }

    // ------------------------------------------------------------------ staff (rule 21)

    private static void staff(Set<String> codes, SyirkahContract c, StaffContract s) {
        for (FormationFinding f : StaffContractChecker.check(c, s)) {
            codes.add(f.code());
        }
    }

    private static void staffScenarios(Set<String> codes) {
        SyirkahContract c = mudharabah(100_000_000, ratio(A, 40, B, 60));
        StaffContract.Wage fixed = new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, idr(3_000_000),
                "monthly");
        staff(codes, c, new StaffContract(StaffContract.Employer.INDIVIDUAL_PARTNER, B, " ", null, "", fixed,
                List.of("Uniform"), false));
        staff(codes, c, new StaffContract(StaffContract.Employer.SYIRKAH, null, "Sales", "12 months", "8 hours",
                new StaffContract.Wage(StaffContract.WageBasis.PERCENT_OF_SALES, null, null), List.of(), false));
        staff(codes, c, new StaffContract(StaffContract.Employer.SYIRKAH, null, "Sales", "12 months", "8 hours",
                new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, null, null), List.of(), false));
        staff(codes, c, new StaffContract(StaffContract.Employer.SYIRKAH, null, "Sales", "12 months", "8 hours",
                new StaffContract.Wage(StaffContract.WageBasis.FIXED_AMOUNT, Money.of(BigDecimal.TEN,
                        Currency.of("USD")), "monthly"), List.of(), false));
    }

    // ------------------------------------------------------------------ agriculture (rule 26)

    private static void agricultureScenarios(Set<String> codes) {
        List<FormationFinding> all = new ArrayList<>();
        all.addAll(AgricultureChecker.check(new AgricultureChecker.Arrangement(AgricultureChecker.Kind.MUZARAAH, true)));
        all.addAll(AgricultureChecker.check(new AgricultureChecker.Arrangement(AgricultureChecker.Kind.MUSAQAH, false)));
        all.forEach(f -> codes.add(f.code()));
    }
}
