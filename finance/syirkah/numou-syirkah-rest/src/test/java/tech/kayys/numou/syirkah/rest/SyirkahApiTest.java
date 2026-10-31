package tech.kayys.numou.numourest;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.numouadapter.memory.*;
import tech.kayys.numou.numouapplication.StaffContractService;
import tech.kayys.numou.numouapplication.port.TradingRecordPort;
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
                result(revenue), null, "distribute", null));
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
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), result("1"), null, "burn", null)));
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

    // ------------------------------------------------------------ lifecycle (partner death)

    static final String C = "00000000-0000-0000-0000-00000000000c";

    private static ContractDto heirContract() {
        return new ContractDto(2, "inan", "IDR",
                List.of(new PartnerDto(A, "A", "partner", true), new PartnerDto(C, "C", "partner", true)),
                List.of(new CapitalDto(A, "cash", new BigDecimal("100000000"), null)),
                Map.of(A, new BigDecimal("40"), C, new BigDecimal("60")), "net_profit", null, List.of(),
                List.of("W1", "W2"), true, LocalDate.parse("2026-02-01"));
    }

    private static LifecycleDto death(boolean withHeirDecision) {
        LocalDate d = LocalDate.parse("2026-02-01");
        return new LifecycleDto("partner_death", List.of(B), LocalDate.parse("2026-01-31"),
                List.of(new ConsentDto(A, true, d, "sig-a"), new ConsentDto(C, true, d, "sig-c")), null, List.of(),
                withHeirDecision ? List.of(new SuccessionDto(B, C, true, d, "heirship-c")) : List.of());
    }

    @Test
    void partnerDeathWithHeirContinuingGoesThroughApproval() {
        String id = open();
        api.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                result("10000000"), null, "retain", null));
        // no heir decision: refused when proposed, nothing waits for a checker
        assertThrows(BusinessRuleViolation.class,
                () -> api.proposeRevision(id, new ReviseRequest(heirContract(), null, death(false))));
        assertTrue(api.pending().isEmpty());

        ApprovalView p = api.proposeRevision(id, new ReviseRequest(heirContract(), null, death(true)));
        assertEquals("PENDING", p.status());
        assertEquals(1, api.get(id).summary().contractVersion());
        ctx.actor("checker");
        assertEquals("APPROVED", api.approve(p.id()).status());
        assertEquals(2, api.get(id).summary().contractVersion());
        assertTrue(api.reconciliation(id).balanced());
    }

    @Test
    void lifecycleInputIsValidated() {
        String id = open();
        assertThrows(IllegalArgumentException.class, () -> api.proposeRevision(id, new ReviseRequest(heirContract(),
                null, new LifecycleDto("explode", List.of(), LocalDate.parse("2026-02-01"), List.of(), null,
                        List.of(), List.of()))));
    }

    @Test
    void tradingPeriodCloseWorksOutCostOfGoodsSold() {
        String id = open();
        // sales 2,000,000; stock 500,000 + purchases 1,000,000 - counted stock 300,000 -> HPP 1,200,000; costs 300,000
        CloseView c = api.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), null, null, "retain", new TradingDto(new BigDecimal("2000000"), null,
                        null, new BigDecimal("500000"), new BigDecimal("1000000"), null, null,
                        new BigDecimal("300000"), new BigDecimal("300000"), null)));
        assertEquals(0, new BigDecimal("500000").compareTo(c.netResult()));
        assertTrue(api.reconciliation(id).balanced());
    }

    @Test
    void tradingAndResultTogetherAreRejected() {
        String id = open();
        assertThrows(IllegalArgumentException.class, () -> api.closePeriod(id, new ClosePeriodRequest(null,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), result("1"), null, "retain",
                new TradingDto(BigDecimal.ONE, null, null, null, null, null, null, null, null, null))));
    }

    @Test
    void rulesAreListedWithoutATenantAndMatchTheCatalog() {
        RulesView v = api.rules();
        assertEquals(26, v.count());
        assertEquals(26, v.rules().size());
        assertFalse(v.frozen());
        assertNull(v.adviserSignoff());
        assertEquals(tech.kayys.numou.numoudomain.ShariaRuleCatalog.fingerprint(), v.fingerprint());
        assertEquals(tech.kayys.numou.numoudomain.ShariaRuleCatalog.render(), api.rulesMarkdown());
        assertTrue(v.rules().get(0).codes().contains("PROFIT_ON_REVENUE"));
    }

    @Test
    void staffAreEngagedOnlyOnAFixedWageAndListedBack() {
        InMemoryStaffRecordPort store = new InMemoryStaffRecordPort();
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts);
        SyirkahApi withStaff = new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC)), new SyirkahIntegrityAuditor(repo, repo), repo, ctx,
                new StaffContractService(repo, store, tx, Clock.system(ZoneOffset.UTC)));
        String id = withStaff.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();

        StaffView v = withStaff.engageStaff(id, new StaffRequest("SYIRKAH", null, "Cashier", "12 months",
                "8 hours a day", "FIXED_AMOUNT", new BigDecimal("3000000"), "monthly", List.of(), false));
        assertEquals(1, v.sequence());
        assertEquals(1, v.contractVersion());
        assertEquals(0, new BigDecimal("3000000").compareTo(v.wageAmount()));
        assertThrows(BusinessRuleViolation.class, () -> withStaff.engageStaff(id, new StaffRequest("SYIRKAH", null,
                "Cashier", "12 months", "8 hours a day", "PERCENT_OF_SALES", null, null, List.of(), false)));
        assertEquals(1, withStaff.staffContracts(id).size(), "a refused contract must not be stored");
    }

    @Test
    void statementsInTheBooksLayout() {
        String id = open();
        CloseView c = close(id, "2026-01-01", "2026-01-31", "10000000");
        IncomeStatementView is = api.incomeStatement(id, c.id(), "Toko Maju");
        assertEquals("Toko Maju", is.name());
        assertEquals("PROFIT", is.result());
        assertEquals(0, new BigDecimal("10000000").compareTo(is.netResult()));
        BalanceSheetView bs = api.balanceSheet(id, new BalanceSheetRequest(null, LocalDate.parse("2026-01-31"),
                List.of(new StatementLineDto("CURRENT", "Cash", new BigDecimal("110000000"))), List.of()));
        assertTrue(bs.balanced(), "assets " + bs.totalAssets() + " vs claims " + bs.totalLiabilitiesAndCapital());
        assertEquals(2, bs.capital().size());
        assertThrows(IllegalArgumentException.class, () -> api.balanceSheet(id, new BalanceSheetRequest(null, null,
                List.of(), List.of())));
    }

    @Test
    void tradingFiguresBehindAClosePeriodAreKeptAndReadable() {
        InMemoryTradingRecordPort store = new InMemoryTradingRecordPort();
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts);
        SyirkahApi a = new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC)), new SyirkahIntegrityAuditor(repo, repo), repo, ctx, null, store);
        String id = a.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();
        TradingDto t = new TradingDto(new BigDecimal("1000"), null, null, new BigDecimal("200"),
                new BigDecimal("600"), null, null, new BigDecimal("300"), new BigDecimal("50"), null);
        CloseView c = a.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), null, null, "retain", t));
        TradingDto back = a.closeTrading(id, c.id());
        assertEquals(0, new BigDecimal("300").compareTo(back.closingStock()));
        assertEquals(0, new BigDecimal("1000").compareTo(back.sales()));
        // cost of goods sold = 200 + 600 - 300 = 500, so net = 1000 - 500 - 50
        assertEquals(0, new BigDecimal("450").compareTo(c.netResult()));
        String other = a.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-28"), result("10"), null, "retain", null)).id();
        assertThrows(tech.kayys.numou.numoudomain.SyirkahNotFoundException.class, () -> a.closeTrading(id, other));
    }

    @Test
    void lifecycleRecordsAreReadableAfterAnApprovedDeathRevision() {
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts, Clock.system(ZoneOffset.UTC),
                tech.kayys.numou.numouapplication.FormationEnforcement.OFF, null, new InMemoryLifecycleRecordPort());
        SyirkahApi a = new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC)), new SyirkahIntegrityAuditor(repo, repo), repo, ctx);
        String id = a.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();
        assertTrue(a.lifecycleRecords(id).isEmpty());
        a.closePeriod(id, new ClosePeriodRequest(null, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"),
                result("10000000"), null, "retain", null));
        ApprovalView p = a.proposeRevision(id, new ReviseRequest(heirContract(), null, death(true)));
        ctx.actor("checker");
        a.approve(p.id());
        var recs = a.lifecycleRecords(id);
        assertEquals(1, recs.size());
        assertEquals(2, recs.get(0).contractVersion());
        assertEquals("PARTNER_DEATH", recs.get(0).lifecycle().kind());
        assertEquals(1, recs.get(0).lifecycle().successions().size());
        assertEquals(2, recs.get(0).lifecycle().consents().size());
    }

    @Test
    void capitalChangesRollForwardForTheRequestedDays() {
        String id = open();
        close(id, "2026-01-01", "2026-01-31", "10000000");
        CapitalChangesView v = api.capitalChanges(id, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
        assertEquals(2, v.rows().size());
        for (CapitalChangeRow r : v.rows()) {
            BigDecimal sum = r.opening().add(r.contributions()).subtract(r.returns()).add(r.retainedProfit())
                    .add(r.capitalRestored()).subtract(r.lossesAllocated()).add(r.deficitReclassified());
            assertEquals(0, sum.compareTo(r.closing()), "roll-forward must add up for " + r.name());
        }
        assertThrows(IllegalArgumentException.class, () -> api.capitalChanges(id, LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-01-01")));
    }

    @Test
    void aFailedTradingWriteSurfacesAsAnErrorInsideTheUnitOfWork() {
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts);
        TradingRecordPort failing = new InMemoryTradingRecordPort() {
            @Override
            public synchronized void record(tech.kayys.numou.foundation.domain.tenant.TenantId t,
                                            tech.kayys.numou.numoudomain.SyirkahId s,
                                            tech.kayys.numou.numoudomain.CloseId c,
                                            tech.kayys.numou.numoudomain.TradingResultCalculator.Input in,
                                            java.time.Instant at) {
                throw new IllegalStateException("disk full");
            }
        };
        SyirkahApi a = new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC)), new SyirkahIntegrityAuditor(repo, repo), repo, ctx, null, failing, tx);
        String id = a.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();
        TradingDto t = new TradingDto(new BigDecimal("1000"), null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> a.closePeriod(id, new ClosePeriodRequest(null,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"), null, null, "retain", t)));
    }

    @Test
    void balanceSheetReadsAssetsFromTheBooksWhenNoneAreSent() {
        LocalTransactionPort tx = new LocalTransactionPort();
        SyirkahApplicationService svc = new SyirkahApplicationService(repo, new InMemoryJournalPostingPort(),
                new CollectingEventPublisher(), tx, accounts);
        tech.kayys.numou.numouapplication.port.BusinessAssetsPort books = (t, sid) -> java.util.Optional.of(
                new tech.kayys.numou.numouapplication.port.BusinessAssetsPort.Snapshot(
                        List.of(new tech.kayys.numou.numoudomain.BookStatements.AssetLine(
                                tech.kayys.numou.numoudomain.BookStatements.AssetGroup.CURRENT, "1100 Cash",
                                tech.kayys.numou.foundation.domain.valueobject.Money.of(new BigDecimal("100000000"),
                                        tech.kayys.numou.foundation.domain.valueobject.Currency.of("IDR")))),
                        List.of()));
        SyirkahApi a = new SyirkahApi(svc, new GovernedSyirkahService(svc, new InMemoryApprovalPort(), tx,
                Clock.system(ZoneOffset.UTC)), new SyirkahIntegrityAuditor(repo, repo), repo, ctx, null, null, tx,
                books);
        String id = a.open(new OpenRequest(null, 0, contract(), null, null)).summary().id();
        BalanceSheetView bs = a.balanceSheet(id, new BalanceSheetRequest("Toko", LocalDate.parse("2026-01-31"), null,
                null));
        assertEquals(1, bs.assets().size());
        assertTrue(bs.balanced());
        // without a source of books the caller must send the figures
        assertThrows(IllegalArgumentException.class, () -> api.balanceSheet(id, new BalanceSheetRequest(null,
                LocalDate.parse("2026-01-31"), null, null)));
    }

    @Test
    void previewShowsTheAllocationButStoresNothing() {
        String id = open();
        ClosePreview p = api.previewClose(id, new ClosePeriodRequest(null, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"), result("10000000"), null, "distribute", null));
        assertEquals("PROFIT", p.result());
        assertEquals(2, p.lines().size());
        BigDecimal shares = p.lines().stream().map(PreviewLine::profitShare).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("10000000").compareTo(shares));
        // nothing happened
        assertEquals(0, api.get(id).summary().closeCount());
        assertTrue(api.closes(id).isEmpty());
        // the real close gives the same shares
        CloseView real = close(id, "2026-01-01", "2026-01-31", "10000000");
        assertEquals(0, p.netResult().compareTo(real.netResult()));
        // a bad period is refused by the preview exactly as by the close
        assertThrows(RuntimeException.class, () -> api.previewClose(id, new ClosePeriodRequest(null,
                LocalDate.parse("2026-01-15"), LocalDate.parse("2026-01-20"), result("1"), null, "retain", null)));
    }

    @Test
    void partnerStatementJsonMatchesTheLedger() {
        String id = open();
        close(id, "2026-01-01", "2026-01-31", "10000000");
        PartnerStatementView v = api.statement(id, A);
        assertEquals(A, v.partnerId());
        assertFalse(v.lines().isEmpty());
        MovementLineView last = v.lines().get(v.lines().size() - 1);
        assertEquals(0, last.capitalBalance().compareTo(v.closing().capitalBalance()));
        assertEquals(0, last.profitPayable().compareTo(v.closing().profitPayable()));
        assertThrows(IllegalArgumentException.class, () -> api.statement(id, "not-a-uuid"));
    }
}
