package tech.kayys.numou.syirkah.adapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.syirkah.adapter.memory.InMemoryJournalPostingPort;
import tech.kayys.numou.syirkah.adapter.memory.InMemoryOutbox;
import tech.kayys.numou.syirkah.adapter.memory.InMemorySyirkahRepository;
import tech.kayys.numou.syirkah.adapter.memory.LocalTransactionPort;
import tech.kayys.numou.syirkah.application.SyirkahAccountMap;
import tech.kayys.numou.syirkah.application.SyirkahApplicationService;
import tech.kayys.numou.syirkah.application.command.Commands;
import tech.kayys.numou.syirkah.application.ops.OutboxRecord;
import tech.kayys.numou.syirkah.application.ops.OutboxRelay;
import tech.kayys.numou.syirkah.application.ops.SyirkahIntegrityAuditor;
import tech.kayys.numou.syirkah.application.port.SyirkahRepository;
import tech.kayys.numou.syirkah.application.query.SyirkahSummary;
import tech.kayys.numou.syirkah.domain.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class OperationsTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");

    private final TenantId tenant = TenantId.generate();
    private final InMemorySyirkahRepository repo = new InMemorySyirkahRepository();
    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final SyirkahApplicationService service = new SyirkahApplicationService(repo,
            new InMemoryJournalPostingPort(), outbox, new LocalTransactionPort(), new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100",
            SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300",
            SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300")));

    private static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), "IDR");
    }

    private static SyirkahContract contract() {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, new BigDecimal("40"));
        n.put(B, new BigDecimal("60"));
        return new SyirkahContract(1, SyirkahType.INAN, Currency.of("IDR"),
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, idr(100_000_000))), ProfitSharingRatio.ofPercent(n),
                ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, LocalDate.parse("2026-01-01"));
    }

    private SyirkahId open(TenantId t) {
        SyirkahId id = SyirkahId.generate();
        service.open(new Commands.Open(t, id, contract(), 0));
        return id;
    }

    private void close(SyirkahId id, int month) {
        LocalDate start = LocalDate.parse("2026-01-01").plusMonths(month);
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), start, start.plusDays(27),
                FinancialResult.of(idr(10_000_000), idr(0), idr(0), idr(0)), ManagerFault.none(),
                ProfitDisposition.DISTRIBUTE));
    }

    // ------------------------------------------------------------------ catalog

    @Test
    void catalogPagesByIdFiltersStatusAndIsolatesTenants() {
        List<SyirkahId> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(open(tenant));
        }
        open(TenantId.generate());
        close(ids.get(0), 0);
        close(ids.get(0), 1);

        List<SyirkahSummary> all = new ArrayList<>();
        Optional<SyirkahId> cursor = Optional.empty();
        while (true) {
            List<SyirkahSummary> page = repo.page(tenant, Optional.empty(), cursor, 2);
            all.addAll(page);
            if (page.size() < 2) {
                break;
            }
            cursor = Optional.of(page.get(page.size() - 1).id());
        }
        assertEquals(5, all.size());
        List<String> order = all.stream().map(s -> s.id().value().toString()).toList();
        assertEquals(order.stream().sorted().toList(), order);
        assertEquals(5, new HashSet<>(order).size());

        SyirkahSummary withCloses = all.stream().filter(s -> s.id().equals(ids.get(0))).findFirst().orElseThrow();
        assertEquals(2, withCloses.closeCount());
        assertEquals(LocalDate.parse("2026-02-28"), withCloses.latestCloseEnd());
        assertEquals(2, withCloses.partnerCount());
        assertEquals(SyirkahType.INAN, withCloses.type());
        assertEquals("IDR", withCloses.currency());

        assertEquals(5, repo.page(tenant, Optional.of(SyirkahStatus.ACTIVE), Optional.empty(), 500).size());
        assertEquals(0, repo.page(tenant, Optional.of(SyirkahStatus.TERMINATED), Optional.empty(), 500).size());
        assertThrows(IllegalArgumentException.class, () -> repo.page(tenant, Optional.empty(), Optional.empty(), 0));
    }

    // ------------------------------------------------------------------ auditor

    @Test
    void auditorReportsHealthyLedgersAndIsolatesCorruptOnes() {
        SyirkahId good = open(tenant);
        SyirkahId bad = open(tenant);
        close(good, 0);
        close(bad, 0);

        assertTrue(new SyirkahIntegrityAuditor(repo, repo).audit(tenant).ok());
        assertEquals(2, new SyirkahIntegrityAuditor(repo, repo).audit(tenant).checked());

        SyirkahRepository corrupting = new SyirkahRepository() {
            @Override
            public Optional<Syirkah> find(TenantId t, SyirkahId id) {
                if (id.equals(bad)) {
                    throw new IllegalStateException("stored close hash mismatch");
                }
                return repo.find(t, id);
            }

            @Override
            public void save(Syirkah s, int v) {
                repo.save(s, v);
            }
        };
        SyirkahIntegrityAuditor.Report report = new SyirkahIntegrityAuditor(corrupting, repo).audit(tenant);
        assertFalse(report.ok());
        assertEquals(2, report.checked());
        assertEquals(1, report.findings().size());
        assertEquals(bad, report.findings().get(0).id());
        assertTrue(report.findings().get(0).problems().get(0).contains("hash mismatch"));
    }

    // ------------------------------------------------------------------ outbox relay

    private OutboxRelay relay() {
        return new OutboxRelay(outbox, new LocalTransactionPort(), Clock.systemUTC());
    }

    @Test
    void relayShipsEventsOnceInOrderAndNothingTwice() {
        SyirkahId id = open(tenant);
        close(id, 0);
        int pending = outbox.unpublishedCount(tenant);
        assertTrue(pending >= 2);

        List<OutboxRecord> shipped = new ArrayList<>();
        assertEquals(pending, relay().drainAll(tenant, 1, 100, shipped::add));
        assertEquals(pending, shipped.size());
        List<Long> seqs = shipped.stream().map(OutboxRecord::sequence).toList();
        assertEquals(seqs.stream().sorted().toList(), seqs);
        assertEquals("syirkah.opened", shipped.get(0).eventType());
        assertEquals(id, shipped.get(0).syirkahId());
        assertEquals(0, outbox.unpublishedCount(tenant));
        assertEquals(0, relay().drain(tenant, 10, shipped::add));
    }

    @Test
    void relayKeepsOrderWhenTheSinkFailsAndResumesAtTheFailedEvent() {
        SyirkahId id = open(tenant);
        close(id, 0);
        close(id, 1);
        int pending = outbox.unpublishedCount(tenant);
        assertTrue(pending >= 3);

        List<OutboxRecord> shipped = new ArrayList<>();
        int[] calls = {0};
        RuntimeException boom = assertThrows(RuntimeException.class, () -> relay().drain(tenant, 10, r -> {
            if (++calls[0] == 3) {
                throw new IllegalStateException("broker down");
            }
            shipped.add(r);
        }));
        assertEquals("broker down", boom.getMessage());
        assertEquals(2, shipped.size());                                  // delivered before the failure are kept
        assertEquals(pending - 2, outbox.unpublishedCount(tenant));       // ... and marked published

        List<OutboxRecord> rest = new ArrayList<>();
        assertEquals(pending - 2, relay().drainAll(tenant, 10, 5, rest::add));
        assertEquals(shipped.get(1).sequence() + 1, rest.get(0).sequence()); // resumes exactly at the failed event
    }

    @Test
    void outboxPublishIsIdempotentPerEventAndRelayValidatesBatchSize() {
        open(tenant);
        int before = outbox.unpublishedCount(tenant);
        List<tech.kayys.numou.foundation.domain.event.DomainEvent> again = new ArrayList<>();
        List<OutboxRecord> first = outbox.claim(tenant, 100);
        assertEquals(before, first.size());
        // re-publishing an already stored event id is a no-op
        outbox.publish(tenant, List.of(new SyirkahEvents.SyirkahOpened(first.get(0).eventId(),
                first.get(0).occurredAt(), first.get(0).syirkahId(), SyirkahType.INAN, 1)));
        assertEquals(before, outbox.unpublishedCount(tenant));
        assertThrows(IllegalArgumentException.class, () -> relay().drain(tenant, 0, r -> { }));
        assertNotNull(again);
    }
}
