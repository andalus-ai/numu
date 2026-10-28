package tech.kayys.numou.numourest;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.numouadapter.memory.*;
import tech.kayys.numou.numouapplication.SyirkahAccountMap;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.governance.GovernedSyirkahService;
import tech.kayys.numou.numouapplication.ops.SyirkahIntegrityAuditor;
import tech.kayys.numou.numoudomain.AkadSection;
import tech.kayys.numou.numoudomain.SyirkahPostingPlan;
import tech.kayys.numou.numouapplication.FormationEnforcement;
import tech.kayys.numou.numourest.SyirkahDtos.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SyirkahApiTest {

    static final String A = "00000000-0000-0000-0000-00000000000a";
    static final String B = "00000000-0000-0000-0000-00000000000b";

    final String tenantId = UUID.randomUUID().toString();
    final InMemorySyirkahRepository repo = new InMemorySyirkahRepository();
    final RequestContextHolder ctx = new RequestContextHolder();
    final SyirkahApi api;
    final SyirkahAccountMap accounts = new SyirkahAccountMap(Map.of(
            SyirkahPostingPlan.AccountRole.CASH, "1100",
            SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL, "3100",
            SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE, "2300",
            SyirkahPostingPlan.AccountRole.PERIOD_RESULT, "3900",
            SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE, "1300"));

    SyirkahApiTest() {
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService service = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts);
        GovernedSyirkahService governed = new GovernedSyirkahService(service, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC));
        api = new SyirkahApi(service, governed, new SyirkahIntegrityAuditor(repo, repo), repo, ctx);
        ctx.verifiedTenant(tenantId);
        ctx.actor("maker");
    }

    static ContractDto contract() {
        return new ContractDto(1, "inan", "IDR",
                List.of(new PartnerDto(A, "A", "partner", true), new PartnerDto(B, "B", "partner", true)),
                List.of(new CapitalDto(A, "cash", new BigDecimal("100000000"), null)),
                Map.of(A, new BigDecimal("40"), B, new BigDecimal("60")), "net_profit", null, List.of(),
                List.of("W1", "W2"), true, LocalDate.parse("2026-01-01"));
    }

    static FinancialResultDto result(String revenue) {
        return new FinancialResultDto(new BigDecimal(revenue), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    String open() {
        return api.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();
    }

    CloseView close(String id, String start, String end, String revenue) {
        return api.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse(start), LocalDate.parse(end),
                result(revenue), null, "distribute"));
    }

    @Test
    void openCloseDistributeAndReadBack() {
        String id = open();
        CloseView c = close(id, "2026-01-01", "2026-01-31", "10000000");
        assertEquals(0, new BigDecimal("10000000").compareTo(c.netResult()));
        api.distribute(id, new PaymentRequest(A, new BigDecimal("4000000"), "pay-a"));
        SyirkahView v = api.get(id);
        PositionView a = v.positions().stream().filter(p -> p.partnerId().equals(A)).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(a.profitPayable()));
        assertTrue(api.reconciliation(id).balanced());
        assertTrue(api.integrity(id).ok());
        assertEquals(1, api.closes(id).size());
        assertTrue(api.statementCsv(id, A).length() > 0);
        assertTrue(api.audit().ok());
    }

    @Test
    void listPagesWithCursor() {
        for (int i = 0; i < 5; i++) {
            open();
        }
        Page p1 = api.list(null, null, 2);
        assertEquals(2, p1.items().size());
        assertNotNull(p1.nextCursor());
        Page p2 = api.list(null, p1.nextCursor(), 2);
        Page p3 = api.list(null, p2.nextCursor(), 2);
        assertEquals(1, p3.items().size());
        assertNull(p3.nextCursor());
        Set<String> seen = new HashSet<>();
        for (Page p : List.of(p1, p2, p3)) {
            p.items().forEach(i -> assertTrue(seen.add(i.id())));
        }
        assertEquals(5, seen.size());
        assertThrows(IllegalArgumentException.class, () -> api.list(null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> api.list("nonsense", null, 10));
    }

    @Test
    void openIsIdempotentOnlyByRejectingDuplicateClientId() {
        String id = UUID.randomUUID().toString();
        api.open(new OpenRequest(id, 0, contract(), null, null));
        assertThrows(BusinessRuleViolation.class, () -> api.open(new OpenRequest(id, 0, contract(), null, null)));
    }

    @Test
    void tenantMustBeVerifiedAndActorPresent() {
        RequestContextHolder anon = new RequestContextHolder();
        anon.unverifiedTenant(tenantId);
        anon.actor("x");
        SyirkahApi untrusted = apiFor(anon);
        assertThrows(SecurityException.class, untrusted::pending);
        RequestContextHolder noActor = new RequestContextHolder();
        noActor.verifiedTenant(tenantId);
        assertThrows(SecurityException.class, () -> apiFor(noActor)
                .proposeStartLiquidation(UUID.randomUUID().toString()));
    }

    private SyirkahApi apiFor(RequestContextHolder c) {
        SyirkahApplicationService svc = apiService();
        return new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(),
                new LocalTransactionPort(), Clock.system(ZoneOffset.UTC)),
                new SyirkahIntegrityAuditor(repo, repo), repo, c);
    }

    private SyirkahApplicationService apiService() {
        return new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(), new CollectingEventPublisher(),
                new LocalTransactionPort(), accounts);
    }

    @Test
    void tenantsAreIsolated() {
        String id = open();
        RequestContextHolder other = new RequestContextHolder();
        other.verifiedTenant(UUID.randomUUID().toString());
        other.actor("x");
        SyirkahApi otherApi = apiFor(other);
        assertThrows(BusinessRuleViolation.class, () -> otherApi.get(id));
        assertTrue(otherApi.list(null, null, 10).items().isEmpty());
    }

    @Test
    void badInputIsReportedAsIllegalArgument() {
        String id = open();
        assertThrows(IllegalArgumentException.class, () -> api.get("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> api.closePeriod(id, null));
        assertThrows(IllegalArgumentException.class, () -> api.closePeriod(id, new ClosePeriodRequest(null,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), result("1"), null, "burn")));
        assertThrows(IllegalArgumentException.class, () -> api.distribute(id, new PaymentRequest(A, null, "x")));
    }

    @Test
    void correctionIsGovernedAndCascadesOverLaterCloses() {
        String id = open();
        CloseView c1 = close(id, "2026-01-01", "2026-01-31", "20000000");
        close(id, "2026-02-01", "2026-02-28", "10000000");

        ApprovalView proposal = api.proposeCorrection(id, new CorrectCloseRequest(c1.id(), null,
                result("5000000"), null, "distribute"));
        assertEquals("PENDING", proposal.status());
        assertEquals(2, api.closes(id).stream().filter(c -> c.status().equals("ACTIVE")).count());
        assertEquals(1, api.pending().size());

        // the proposer cannot approve their own proposal
        assertThrows(BusinessRuleViolation.class, () -> api.approve(proposal.id()));

        ctx.actor("checker");
        assertEquals("APPROVED", api.approve(proposal.id()).status());
        assertTrue(api.pending().isEmpty());
        List<CloseView> closes = api.closes(id);
        assertEquals(2, closes.stream().filter(c -> c.status().equals("ACTIVE")).count());
        assertEquals(2, closes.stream().filter(c -> c.status().equals("REVERSED")).count());
        assertEquals(4, closes.size());
        assertTrue(api.reconciliation(id).balanced());
        assertTrue(api.integrity(id).ok());
    }

    @Test
    void correctionWithWrongNumberOfReplacementIdsIsRejected() {
        String id = open();
        CloseView c1 = close(id, "2026-01-01", "2026-01-31", "20000000");
        close(id, "2026-02-01", "2026-02-28", "10000000");
        assertThrows(IllegalArgumentException.class, () -> api.proposeCorrection(id, new CorrectCloseRequest(c1.id(),
                List.of(UUID.randomUUID().toString()), result("5000000"), null, "distribute")));
        assertThrows(BusinessRuleViolation.class, () -> api.proposeCorrection(id, new CorrectCloseRequest(
                UUID.randomUUID().toString(), null, result("5"), null, "distribute")));
    }

    @Test
    void rejectNeedsReasonAndEndsTheApproval() {
        String id = open();
        ApprovalView p = api.proposeStartLiquidation(id);
        ctx.actor("checker");
        assertThrows(IllegalArgumentException.class, () -> api.reject(p.id(), new RejectRequest(" ")));
        ApprovalView r = api.reject(p.id(), new RejectRequest("not yet"));
        assertEquals("REJECTED", r.status());
        assertThrows(BusinessRuleViolation.class, () -> api.approve(p.id()));
        assertEquals("ACTIVE", api.get(id).summary().status());
    }

    @Test
    void liquidationBySalesGoesThroughApproval() {
        String id = open();
        close(id, "2026-01-01", "2026-01-31", "10000000");
        ApprovalView start = api.proposeStartLiquidation(id);
        ctx.actor("checker");
        api.approve(start.id());
        ctx.actor("maker");
        ApprovalView liq = api.proposeLiquidation(id, new LiquidateRequest(
                List.of(new AssetSaleDto("stock", new BigDecimal("100000000"), "outsider", null, true)),
                null, false, BigDecimal.ZERO, LocalDate.parse("2026-03-01"), null));
        assertEquals("PENDING", liq.status());
        ctx.actor("checker");
        api.approve(liq.id());
        SyirkahView v = api.get(id);
        assertEquals("TERMINATED", v.summary().status());
        assertTrue(api.reconciliation(id).balanced());
    }

    // ------------------------------------------------------------ akad formation

    static FormationDto formation(boolean sameSession) {
        LocalDate signed = LocalDate.parse("2026-01-01");
        return new FormationDto(
                List.of(new PartyDto(A, LocalDate.parse("1990-05-17"), "Jakarta", true, true, true, "ijab and qabul"),
                        new PartyDto(B, LocalDate.parse("1991-06-18"), "Bandung", true, true, true, "ijab and qabul")),
                "Retail of household goods", new LawfulnessReviewDto("Sharia adviser", signed, true), null, null,
                sameSession, false, Arrays.stream(AkadSection.values()).map(Enum::name).toList(), "Jakarta", signed,
                List.of(new WitnessDto("W1", "male"), new WitnessDto("W2", "male")));
    }

    @Test
    void aValidlyFormedAkadOpensAndADefectiveOneCreatesNothing() {
        String id = UUID.randomUUID().toString();
        assertEquals(id, api.open(new OpenRequest(id, 0, contract(), formation(true), null)).summary().id());

        String bad = UUID.randomUUID().toString();
        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class,
                () -> api.open(new OpenRequest(bad, 0, contract(), formation(false), null)));
        assertTrue(e.getMessage().contains("SESSION_NOT_UNIFIED"));
        assertThrows(RuntimeException.class, () -> api.get(bad));
    }

    @Test
    void policyFieldsOverrideTheBookDefaultsAndUnknownValuesAreRejected() {
        FormationDto oneWomanOneMan = new FormationDto(formation(true).parties(), "Retail",
                new LawfulnessReviewDto("Sharia adviser", LocalDate.parse("2026-01-01"), true), null, null, true, false,
                Arrays.stream(AkadSection.values()).map(Enum::name).toList(), "Jakarta", LocalDate.parse("2026-01-01"),
                List.of(new WitnessDto("W1", "male"), new WitnessDto("W2", "female")));
        assertThrows(BusinessRuleViolation.class,
                () -> api.open(new OpenRequest(null, 0, contract(), oneWomanOneMan, null)));
        assertNotNull(api.open(new OpenRequest(null, 0, contract(), oneWomanOneMan,
                new FormationPolicyDto("count_only", null, null))).summary().id());
        assertThrows(IllegalArgumentException.class, () -> api.open(new OpenRequest(null, 0, contract(),
                formation(true), new FormationPolicyDto("whatever", null, null))));
    }

    @Test
    void whenTheDeploymentRequiresFormationAPlainOpenIsRefused() {
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService strict = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts, Clock.system(ZoneOffset.UTC),
                FormationEnforcement.REQUIRED, new InMemoryAkadFormationPort());
        GovernedSyirkahService governed = new GovernedSyirkahService(strict, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC));
        SyirkahApi strictApi = new SyirkahApi(strict, governed, new SyirkahIntegrityAuditor(repo, repo), repo, ctx);
        assertThrows(BusinessRuleViolation.class,
                () -> strictApi.open(new OpenRequest(null, 0, contract(), null, null)));
        assertNotNull(strictApi.open(new OpenRequest(null, 0, contract(), formation(true), null)).summary().id());
    }
}
