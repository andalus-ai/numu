package tech.kayys.numou.numouadapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouadapter.memory.CollectingEventPublisher;
import tech.kayys.numou.numouadapter.memory.InMemoryJournalPostingPort;
import tech.kayys.numou.numouadapter.memory.InMemoryLifecycleRecordPort;
import tech.kayys.numou.numouadapter.memory.InMemorySyirkahRepository;
import tech.kayys.numou.numouadapter.memory.LocalTransactionPort;
import tech.kayys.numou.numouapplication.FormationEnforcement;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numoudomain.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleFlowTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");
    private static final PartnerId C = PartnerId.of("00000000-0000-0000-0000-00000000000c");
    private static final Currency IDR = Currency.of("IDR");
    private static final LocalDate JAN1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN31 = LocalDate.parse("2026-01-31");
    private static final LocalDate FEB1 = LocalDate.parse("2026-02-01");

    private final TenantId tenant = TenantId.generate();
    private final InMemorySyirkahRepository repo = new InMemorySyirkahRepository();
    private final InMemoryLifecycleRecordPort records = new InMemoryLifecycleRecordPort();
    private final CollectingEventPublisher published = new CollectingEventPublisher();
    private final SyirkahAccountMap accounts = new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100",
            SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300",
            SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300"));
    private final SyirkahApplicationService service = new SyirkahApplicationService(repo,
            new InMemoryJournalPostingPort(), published, new LocalTransactionPort(), accounts,
            Clock.system(ZoneOffset.UTC), FormationEnforcement.OFF, null, records);

    private static Money idr(long v) {
        return Money.of(BigDecimal.valueOf(v), "IDR");
    }

    private static ProfitSharingRatio half(PartnerId x, PartnerId y) {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(x, new BigDecimal("50"));
        n.put(y, new BigDecimal("50"));
        return ProfitSharingRatio.ofPercent(n);
    }

    private static Partner p(PartnerId id) {
        return new Partner(id, id.value().toString(), PartnerRole.PARTNER, true);
    }

    private static SyirkahContract v(int version, PartnerId x, PartnerId y, LocalDate from) {
        return new SyirkahContract(version, SyirkahType.INAN, IDR, List.of(p(x), p(y)),
                List.of(CapitalContribution.cash(x, idr(100_000_000)), CapitalContribution.cash(y, idr(100_000_000))),
                half(x, y), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true, from);
    }

    private SyirkahId openAndClose() {
        SyirkahId id = SyirkahId.generate();
        service.open(new Commands.Open(tenant, id, v(1, A, B, JAN1), 0));
        service.closePeriod(new Commands.ClosePeriod(tenant, id, CloseId.generate(), JAN1, JAN31,
                FinancialResult.of(idr(1_000_000), idr(0), idr(0), idr(0)), ManagerFault.none(),
                ProfitDisposition.RETAIN));
        return id;
    }

    private static LifecycleConsent yes(PartnerId p) {
        return new LifecycleConsent(p, true, FEB1, "sig-" + p.value());
    }

    @Test
    void heirContinuesAfterDeathAndTheRecordIsKept() {
        SyirkahId id = openAndClose();
        LifecycleChange death = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31,
                List.of(yes(A), yes(C)), null, List.of(),
                List.of(new Succession(B, C, true, FEB1, "heirship-c")));
        service.reviseWithLifecycle(new Commands.ReviseContract(tenant, id, v(2, A, C, FEB1), Set.of()), death);

        assertEquals(2, repo.find(tenant, id).orElseThrow().contract().version());
        assertTrue(repo.find(tenant, id).orElseThrow().contract().hasPartner(C));
        var kept = records.list(tenant, id);
        assertEquals(1, kept.size());
        assertEquals(2, kept.get(0).contractVersion());
        assertEquals(death, kept.get(0).change());
        var died = published.events().stream().filter(e -> e instanceof SyirkahEvents.PartnerDied)
                .map(e -> (SyirkahEvents.PartnerDied) e).toList();
        assertEquals(1, died.size());
        assertEquals(B, died.get(0).partner());
        assertEquals(JAN31, died.get(0).diedOn());
    }

    @Test
    void aRejectedChangeChangesAndRecordsNothing() {
        SyirkahId id = openAndClose();
        LifecycleChange noHeirDecision = new LifecycleChange(LifecycleKind.PARTNER_DEATH, Set.of(B), JAN31,
                List.of(yes(A), yes(C)), null, List.of());
        assertThrows(BusinessRuleViolation.class, () -> service.reviseWithLifecycle(
                new Commands.ReviseContract(tenant, id, v(2, A, C, FEB1), Set.of()), noHeirDecision));
        assertEquals(1, repo.find(tenant, id).orElseThrow().contract().version());
        assertEquals(List.of(), records.list(tenant, id));
        assertTrue(published.events().stream().noneMatch(e -> e instanceof SyirkahEvents.PartnerDied));
    }
}
