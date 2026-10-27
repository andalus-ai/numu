package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.kayys.numou.numoudomain.Fixtures.*;

/**
 * Randomised (seeded, reproducible) checks of the ledger invariants over
 * arbitrary partner sets, ratios and
 * operation sequences: allocations sum exactly, positions never go negative
 * where the rules forbid it,
 * reconciliation balances, hash chains verify and a restored aggregate equals
 * the live one.
 */
class SyirkahPropertyTest {

    private static final int RUNS = 1500;
    private int olderCorrections;
    private int multiCloseCascades;

    private static PartnerId pid(int i) {
        return PartnerId.of(String.format("00000000-0000-0000-0000-%012d", i + 1));
    }

    private static Map<PartnerId, BigDecimal> randomPercents(Random r, int n) {
        int[] cuts = new int[n];
        int left = 100;
        for (int i = 0; i < n - 1; i++) {
            int max = left - (n - 1 - i);
            cuts[i] = 1 + r.nextInt(Math.max(1, max));
            left -= cuts[i];
        }
        cuts[n - 1] = left;
        Map<PartnerId, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            m.put(pid(i), BigDecimal.valueOf(cuts[i]));
        }
        return m;
    }

    @Test
    void apportionmentAlwaysSumsExactlyAndRespectsWeights() {
        Random r = new Random(42);
        for (int run = 0; run < 5000; run++) {
            int n = 1 + r.nextInt(7);
            Map<PartnerId, BigDecimal> w = new TreeMap<>();
            for (int i = 0; i < n; i++) {
                w.put(pid(i), BigDecimal.valueOf(1 + r.nextInt(1000)));
            }
            int scale = r.nextInt(3);
            BigDecimal total = BigDecimal.valueOf(r.nextInt(2_000_000) - 1_000_000, scale);
            Map<PartnerId, BigDecimal> parts = Apportionment.apportion(total, w, scale);
            BigDecimal sum = parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, total.compareTo(sum), "sum mismatch run " + run);
            BigDecimal sumW = w.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
            for (PartnerId k : w.keySet()) {
                BigDecimal exact = total.multiply(w.get(k)).divide(sumW, 30, java.math.RoundingMode.HALF_EVEN);
                assertTrue(parts.get(k).subtract(exact).abs().compareTo(unit) < 0,
                        "share off by more than one unit, run " + run);
            }
        }
    }

    @Test
    void randomLifecyclesKeepEveryInvariant() {
        for (int seed = 0; seed < RUNS; seed++) {
            Random r = new Random(seed);
            try {
                runOne(r);
            } catch (AssertionError | RuntimeException e) {
                throw new AssertionError("seed " + seed + ": " + e, e);
            }
        }
        // the generator must really exercise correcting older closes, otherwise this test proves nothing about them
        assertTrue(olderCorrections > 200, "older-close corrections exercised: " + olderCorrections);
        assertTrue(multiCloseCascades > 100, "multi-close cascades exercised: " + multiCloseCascades);
    }

    private void runOne(Random r) {
        int n = 2 + r.nextInt(4);
        Map<PartnerId, BigDecimal> pct = randomPercents(r, n);
        boolean mudharabah = r.nextInt(4) == 0 && n >= 2;
        List<Partner> partners = new ArrayList<>();
        List<CapitalContribution> capital = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PartnerRole role = mudharabah ? (i == n - 1 ? PartnerRole.MANAGER : PartnerRole.CAPITAL_PROVIDER)
                    : PartnerRole.PARTNER;
            partners.add(new Partner(pid(i), "P" + i, role, role != PartnerRole.CAPITAL_PROVIDER));
            if (role != PartnerRole.MANAGER) {
                capital.add(CapitalContribution.cash(pid(i), idr(1_000L * (1 + r.nextInt(1_000_000)))));
            }
        }
        if (!mudharabah && capital.stream().noneMatch(c -> c.amount().isPositive())) {
            return;
        }
        SyirkahContract c = new SyirkahContract(1, mudharabah ? SyirkahType.MUDHARABAH : SyirkahType.INAN, IDR,
                partners, capital, ProfitSharingRatio.ofPercent(pct), ProfitBasis.NET_PROFIT, null, Set.of(),
                List.of("W1", "W2"), true, LocalDate.parse("2026-01-01"));
        Syirkah s = Syirkah.open(SyirkahId.generate(), TenantId.generate(), c, 0, NOW);
        LocalDate day = LocalDate.parse("2026-01-01");
        CloseSnapshot last = null;
        int ops = 3 + r.nextInt(12);
        for (int i = 0; i < ops; i++) {
            int op = r.nextInt(6);
            try {
                if (op <= 1 || last == null) {
                    long rev = r.nextInt(5_000_000);
                    long cost = r.nextInt(5_000_000);
                    FinancialResult fr = result(rev, cost / 2, cost / 4, cost - cost / 2 - cost / 4);
                    ManagerFault f = ManagerFault.none();
                    if (mudharabah && fr.netResult().isNegative() && r.nextBoolean()) {
                        f = ManagerFault.of(Money.of(fr.netResult().amount().negate().divide(BigDecimal.valueOf(2),
                                0, java.math.RoundingMode.DOWN), IDR), "fault");
                    }
                    last = s.closePeriod(CloseId.generate(), day, day.plusDays(29), fr, f,
                            r.nextBoolean() ? ProfitDisposition.RETAIN : ProfitDisposition.DISTRIBUTE, NOW);
                    day = day.plusDays(30);
                } else if (op == 2) {
                    PartnerId p = pid(r.nextInt(n));
                    Money pay = s.position(p).payable();
                    if (pay.isPositive()) {
                        long part = Math.max(1, pay.amount().longValue() / (1 + r.nextInt(3)));
                        s.distributeProfit(p, idr(part), "d" + i, NOW);
                    }
                } else if (op == 5) {
                    List<CloseSnapshot> active = s.closes().stream()
                            .filter(x -> x.status() == CloseStatus.ACTIVE).toList();
                    int from = r.nextInt(active.size());
                    List<CloseId> ids = new ArrayList<>();
                    for (int k = from; k < active.size(); k++) {
                        ids.add(CloseId.generate());
                    }
                    long rev = r.nextInt(5_000_000);
                    java.util.List<CloseSnapshot> replaced = s.correctClose(active.get(from).id(), ids,
                            result(rev, rev / 3, 0, 0), ManagerFault.none(),
                            r.nextBoolean() ? ProfitDisposition.RETAIN : ProfitDisposition.DISTRIBUTE, NOW);
                    last = replaced.get(replaced.size() - 1);
                    olderCorrections++;
                    if (replaced.size() > 1) {
                        multiCloseCascades++;
                    }
                } else if (op == 3) {
                    long rev = r.nextInt(5_000_000);
                    last = s.correctLatestClose(last.id(), CloseId.generate(), result(rev, rev / 3, 0, 0),
                            ManagerFault.none(),
                            r.nextBoolean() ? ProfitDisposition.RETAIN : ProfitDisposition.DISTRIBUTE,
                            NOW);
                } else {
                    PartnerId p = pid(r.nextInt(n));
                    Money l = s.position(p).liability();
                    if (l.isPositive()) {
                        s.settleLiability(p, l, "s" + i, NOW);
                    }
                }
            } catch (BusinessRuleViolation expected) {
                // rules may legitimately refuse (e.g. correction after a settlement); state
                // must stay consistent
            }
            check(s);
        }
        if (r.nextInt(3) == 0) {
            // Liquidation is intentionally blocked while any partner still owes a
            // manager-fault liability. Settle those receivables explicitly first;
            // do not weaken the domain rule merely to make randomized tests pass.
            int liabilitySettlement = 0;
            for (PartnerId p : s.positions().keySet()) {
                Money liability = s.position(p).liability();
                if (liability.isPositive()) {
                    s.settleLiability(p, liability, "liquidation-liability-" + liabilitySettlement++, NOW);
                }
            }
            check(s);
            s.startLiquidation(NOW);
            long proceeds = r.nextInt(3_000_000);
            s.liquidate(new LiquidationInput(idr(proceeds), idr(r.nextInt(1_000_000)), true, day), ManagerFault.none(),
                    NOW);
            check(s);
            for (PartnerId p : s.positions().keySet()) {
                assertTrue(s.position(p).basis().isZero(), "basis not closed after liquidation");
                assertTrue(s.position(p).balance().isZero(), "balance not closed after liquidation");
                assertTrue(s.position(p).payable().isZero(), "payable not closed after liquidation");
            }
        }
    }

    private static void check(Syirkah s) {
        EquityReconciliation rec = s.reconcile();
        assertTrue(rec.balanced(), "reconciliation off by " + rec.difference().amount());
        assertTrue(s.verifyIntegrity().isEmpty(), "integrity: " + s.verifyIntegrity());
        for (PartnerPosition p : s.positions().values()) {
            assertTrue(p.payable().amount().signum() >= 0, "negative payable " + p.payable().amount());
            assertTrue(p.liability().amount().signum() >= 0, "negative liability " + p.liability().amount());
        }
        Syirkah copy = Syirkah.restore(s.snapshot());
        assertEquals(s.positions(), copy.positions());
        assertEquals(s.movements(), copy.movements());
    }
}
