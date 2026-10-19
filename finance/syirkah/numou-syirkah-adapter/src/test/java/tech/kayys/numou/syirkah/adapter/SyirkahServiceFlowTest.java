package tech.kayys.numou.syirkah.adapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.adapter.ledger.JournalEntryPostingAdapter;
import tech.kayys.numou.syirkah.adapter.memory.CollectingEventPublisher;
import tech.kayys.numou.syirkah.adapter.memory.InMemoryJournalPostingPort;
import tech.kayys.numou.syirkah.adapter.memory.InMemorySyirkahRepository;
import tech.kayys.numou.syirkah.adapter.memory.LocalTransactionPort;
import tech.kayys.numou.syirkah.application.SyirkahAccountMap;
import tech.kayys.numou.syirkah.application.SyirkahApplicationService;
import tech.kayys.numou.syirkah.application.command.Commands;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahServiceFlowTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    private final TenantId tenant = TenantId.generate();
    private final InMemorySyirkahRepository repo = new InMemorySyirkahRepository();
    private final InMemoryJournalPostingPort journal = new InMemoryJournalPostingPort();
    private final CollectingEventPublisher events = new CollectingEventPublisher();
    private final SyirkahAccountMap accounts = new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100",
            SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300",
            SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300"));
    private final SyirkahApplicationService service =
            new SyirkahApplicationService(repo, journal, events, new LocalTransactionPort(), accounts);

    private static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), "IDR");
    }

    private static SyirkahContract contract() {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, new BigDecimal("40"));
        n.put(B, new BigDecimal("60"));
        return new SyirkahContract(1, SyirkahType.INAN, tech.kayys.numou.foundation.domain.valueobject.Currency.of("IDR"),
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000))), ProfitSharingRatio.ofPercent(n),
                ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, LocalDate.parse("2026-01-01"));
    }

    private SyirkahId openOne() {
        SyirkahId id = SyirkahId.generate();
        service.open(new Commands.Open(tenant, id, contract(), 0));
        return id;
    }

    private BigDecimal net(String account) {
        Money m = journal.trialBalance("IDR").get(account);
        return m == null ? BigDecimal.ZERO : m.amount();
    }

    @Test
    void fullLifecyclePostsBalancedJournalsAndKeepsLedgerConsistent() {
        SyirkahId id = openOne();
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                FinancialResult.of(idr(20_000_000), idr(6_000_000), idr(3_500_000), idr(1_000_000)),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE));
        service.distributeProfit(new Commands.DistributeProfit(tenant, id, A, idr(3_800_000), "pay-a"));
        service.distributeProfit(new Commands.DistributeProfit(tenant, id, B, idr(5_700_000), "pay-b"));

        Syirkah s = service.get(tenant, id);
        assertEquals(0, BigDecimal.ZERO.compareTo(s.position(A).payable().amount()));
        // cash: +100M contribution - 9.5M distributed ; capital 100M ; payable settled ; period result -9.5M profit
        assertEquals(0, new BigDecimal("90500000").compareTo(net("1100")));
        assertEquals(0, new BigDecimal("-100000000").compareTo(net("3100")));
        assertEquals(0, BigDecimal.ZERO.compareTo(net("2300")));
        assertEquals(0, new BigDecimal("9500000").compareTo(net("3900")));
        BigDecimal all = journal.trialBalance("IDR").values().stream().map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ZERO.compareTo(all)); // trial balance nets to zero
        assertTrue(events.events().size() >= 4);
        assertTrue(s.verifyIntegrity().isEmpty());
    }

    @Test
    void retryingADistributionDoesNotDoublePay() {
        SyirkahId id = openOne();
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                FinancialResult.of(idr(10_000_000), idr(0), idr(0), idr(0)), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE));
        Commands.DistributeProfit cmd = new Commands.DistributeProfit(tenant, id, A, idr(1_000_000), "same");
        service.distributeProfit(cmd);
        int journals = journal.posted().size();
        service.distributeProfit(cmd);
        assertEquals(journals, journal.posted().size());
        assertEquals(0, new BigDecimal("3000000").compareTo(service.get(tenant, id).position(A).payable().amount()));
    }

    @Test
    void otherTenantCannotSeeOrChangeTheSyirkah() {
        SyirkahId id = openOne();
        TenantId other = TenantId.generate();
        assertThrows(BusinessRuleViolation.class, () -> service.get(other, id));
        assertThrows(BusinessRuleViolation.class, () -> service.closePeriod(new Commands.ClosePeriod(other, id,
                CloseId.generate(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                FinancialResult.of(idr(1), idr(0), idr(0), idr(0)), ManagerFault.none(), ProfitDisposition.RETAIN)));
    }

    @Test
    void staleWriterIsRejectedByOptimisticLock() {
        SyirkahId id = openOne();
        Syirkah first = repo.find(tenant, id).orElseThrow();
        Syirkah second = repo.find(tenant, id).orElseThrow();
        first.closePeriod(CloseId.generate(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                FinancialResult.of(idr(10), idr(0), idr(0), idr(0)), ManagerFault.none(), ProfitDisposition.DISTRIBUTE,
                java.time.Instant.now());
        int v = first.getVersion();
        repo.save(first, v);
        second.closePeriod(CloseId.generate(), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                FinancialResult.of(idr(20), idr(0), idr(0), idr(0)), ManagerFault.none(), ProfitDisposition.DISTRIBUTE,
                java.time.Instant.now());
        int staleVersion = second.getVersion();
        assertThrows(SyirkahRepository.ConcurrentModificationException.class, () -> repo.save(second, staleVersion));
    }

    @Test
    void failedCommandLeavesNoPartialState() {
        SyirkahId id = openOne();
        int before = service.get(tenant, id).movements().size();
        assertThrows(BusinessRuleViolation.class, () -> service.distributeProfit(
                new Commands.DistributeProfit(tenant, id, A, idr(1), "nothing-to-pay")));
        assertEquals(before, service.get(tenant, id).movements().size());
    }

    @Test
    void movementsPostAsBalancedNumouJournalEntries() {
        Map<String, JournalEntry> saved = new LinkedHashMap<>();
        Map<String, AccountId> chart = new HashMap<>();
        JournalEntryPostingAdapter adapter = new JournalEntryPostingAdapter(
                code -> chart.computeIfAbsent(code, c -> AccountId.generate()),
                new JournalEntryPostingAdapter.Sink() {
                    public boolean exists(String t, String k) {
                        return saved.containsKey(t + "|" + k);
                    }

                    public void save(String t, String k, JournalEntry e) {
                        saved.put(t + "|" + k, e);
                    }
                }, new LedgerId("SHARIAH"));
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, adapter, events,
                new LocalTransactionPort(), accounts);
        SyirkahId id = SyirkahId.generate();
        svc.open(new Commands.Open(tenant, id, contract(), 0));
        svc.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), FinancialResult.of(idr(10_000_000), idr(0), idr(0), idr(0)),
                ManagerFault.none(), ProfitDisposition.RETAIN));
        assertFalse(saved.isEmpty());
        for (JournalEntry e : saved.values()) {
            assertEquals(JournalEntry.EntryStatus.POSTED, e.getStatus());
        }
        // replay of an already-posted source key creates nothing new
        int n = saved.size();
        Syirkah s = svc.get(tenant, id);
        SyirkahPostingPlan.forMovement(s.movements().get(0));
        adapter.post(new tech.kayys.numou.syirkah.application.port.JournalPostingPort.Request(tenant,
                SyirkahPostingPlan.sourceKey(id, s.movements().get(0)), LocalDate.parse("2026-01-01"), "dup",
                List.of()));
        assertEquals(n, saved.size());
    }

    @Test
    void liquidationThroughServiceTerminatesAndPostsClosingJournals() {
        SyirkahId id = openOne();
        service.startLiquidation(new Commands.StartLiquidation(tenant, id));
        LiquidationResult r = service.liquidate(new Commands.Liquidate(tenant, id,
                new LiquidationInput(idr(130_000_000), idr(10_000_000), true, LocalDate.parse("2026-12-31")),
                ManagerFault.none()));
        // residual 120M, claims 100M (A) => profit 20M: A 8M, B 12M; A gets 108M, B 12M
        assertEquals(0, new BigDecimal("108000000").compareTo(r.payouts().get(0).cashReceived().amount()));
        assertEquals(0, new BigDecimal("12000000").compareTo(r.payouts().get(1).cashReceived().amount()));
        assertEquals(SyirkahStatus.TERMINATED, service.get(tenant, id).status());
        // cash after liquidation: 100M in - 120M out + 20M... = contributions 100M - payouts 120M = -20M here
        // because the 20M profit was earned in the business outside this ledger's cash account
        assertEquals(0, new BigDecimal("-20000000").compareTo(net("1100")));
    }

    @Test
    void concurrentDistributionsNeverOverpayAndReplaysApplyOnce() throws Exception {
        SyirkahId id = openOne();
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), FinancialResult.of(idr(10_000_000), idr(0), idr(0), idr(0)),
                ManagerFault.none(), ProfitDisposition.DISTRIBUTE)); // A is owed 4,000,000
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            final int n = i;
            // 12 distinct payments of 1,000,000 (only 4 can succeed) + 12 replays of one shared reference
            final String ref = n < 12 ? "unique-" + n : "shared";
            futures.add(pool.submit(() -> {
                try {
                    service.distributeProfit(new Commands.DistributeProfit(tenant, id, A, idr(1_000_000), ref));
                    return true;
                } catch (BusinessRuleViolation e) {
                    return false;
                }
            }));
        }
        for (java.util.concurrent.Future<Boolean> f : futures) {
            f.get();
        }
        pool.shutdown();
        Syirkah s = service.get(tenant, id);
        BigDecimal paid = new BigDecimal("4000000").subtract(s.position(A).payable().amount());
        assertEquals(0, new BigDecimal("4000000").compareTo(paid)); // exactly the entitlement, never more
        assertTrue(s.position(A).payable().amount().signum() >= 0);
        assertTrue(s.reconcile().balanced());
        assertTrue(s.verifyIntegrity().isEmpty());
        long sharedApplied = s.movements().stream()
                .filter(m -> "pay:shared".equals(m.reference())).count();
        assertTrue(sharedApplied <= 1);
    }
}
