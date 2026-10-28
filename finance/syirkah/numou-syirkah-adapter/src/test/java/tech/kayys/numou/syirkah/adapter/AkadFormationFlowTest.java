package tech.kayys.numou.numouadapter;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.numouadapter.memory.CollectingEventPublisher;
import tech.kayys.numou.numouadapter.memory.InMemoryAkadFormationPort;
import tech.kayys.numou.numouadapter.memory.InMemoryJournalPostingPort;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AkadFormationFlowTest {

    private static final PartnerId A = PartnerId.of("00000000-0000-0000-0000-00000000000a");
    private static final PartnerId B = PartnerId.of("00000000-0000-0000-0000-00000000000b");
    private static final LocalDate SIGNED = LocalDate.parse("2026-01-01");

    private final TenantId tenant = TenantId.generate();
    private final InMemorySyirkahRepository repo = new InMemorySyirkahRepository();
    private final SyirkahAccountMap accounts = new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100",
            SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300",
            SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300"));
    private final SyirkahApplicationService service = new SyirkahApplicationService(repo,
            new InMemoryJournalPostingPort(), new CollectingEventPublisher(), new LocalTransactionPort(), accounts);

    private static SyirkahContract contract() {
        Map<PartnerId, BigDecimal> n = new TreeMap<>();
        n.put(A, new BigDecimal("40"));
        n.put(B, new BigDecimal("60"));
        return new SyirkahContract(1, SyirkahType.INAN, Currency.of("IDR"),
                List.of(new Partner(A, "A", PartnerRole.PARTNER, true), new Partner(B, "B", PartnerRole.PARTNER, true)),
                List.of(CapitalContribution.cash(A, Money.of(BigDecimal.valueOf(100_000_000), "IDR"))),
                ProfitSharingRatio.ofPercent(n), ProfitBasis.NET_PROFIT, null, Set.of(), List.of("W1", "W2"), true,
                SIGNED);
    }

    private static PartyRecord party(PartnerId id) {
        return new PartyRecord(id, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true, "ijab and qabul");
    }

    private static AkadFormation formation(boolean sameSession) {
        return new AkadFormation(List.of(party(A), party(B)), "Retail of household goods",
                new AkadFormation.LawfulnessReview("Sharia adviser", SIGNED, true), Set.of(), List.of(), sameSession,
                false, EnumSet.allOf(AkadSection.class), "Jakarta", SIGNED,
                List.of(new Witness("W1", WitnessSex.MALE), new Witness("W2", WitnessSex.MALE)));
    }

    @Test
    void aValidlyFormedAkadOpensTheSyirkah() {
        SyirkahId id = SyirkahId.generate();
        service.openWithFormation(new Commands.Open(tenant, id, contract(), 0), formation(true),
                FormationPolicy.bookDefault());
        assertTrue(repo.find(tenant, id).isPresent());
    }

    @Test
    void aDefectiveAkadCreatesNothing() {
        SyirkahId id = SyirkahId.generate();
        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class,
                () -> service.openWithFormation(new Commands.Open(tenant, id, contract(), 0), formation(false),
                        FormationPolicy.bookDefault()));
        assertTrue(e.getMessage().contains("SESSION_NOT_UNIFIED"));
        assertEquals(false, repo.find(tenant, id).isPresent());
    }

    @Test
    void whenEnforcementIsRequiredPlainOpenIsRefusedAndOnlyFormationOpenWorks() {
        SyirkahApplicationService strict = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), new LocalTransactionPort(), accounts, Clock.system(ZoneOffset.UTC),
                FormationEnforcement.REQUIRED);
        SyirkahId plain = SyirkahId.generate();
        assertThrows(BusinessRuleViolation.class, () -> strict.open(new Commands.Open(tenant, plain, contract(), 0)));
        assertEquals(false, repo.find(tenant, plain).isPresent());
        SyirkahId checked = SyirkahId.generate();
        strict.openWithFormation(new Commands.Open(tenant, checked, contract(), 0), formation(true),
                FormationPolicy.bookDefault());
        assertTrue(repo.find(tenant, checked).isPresent());
    }

    @Test
    void theFormationRecordIsKeptOnlyWhenTheSyirkahOpens() {
        InMemoryAkadFormationPort store = new InMemoryAkadFormationPort();
        SyirkahApplicationService withStore = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), new LocalTransactionPort(), accounts, Clock.system(ZoneOffset.UTC),
                FormationEnforcement.REQUIRED, store);
        SyirkahId bad = SyirkahId.generate();
        assertThrows(BusinessRuleViolation.class, () -> withStore.openWithFormation(
                new Commands.Open(tenant, bad, contract(), 0), formation(false), FormationPolicy.bookDefault()));
        assertEquals(false, store.find(tenant, bad).isPresent());

        SyirkahId good = SyirkahId.generate();
        withStore.openWithFormation(new Commands.Open(tenant, good, contract(), 0), formation(true),
                FormationPolicy.bookDefault());
        var recorded = store.find(tenant, good).orElseThrow();
        assertEquals(formation(true), recorded.formation());
        assertEquals(FormationPolicy.bookDefault(), recorded.policy());
    }
}
