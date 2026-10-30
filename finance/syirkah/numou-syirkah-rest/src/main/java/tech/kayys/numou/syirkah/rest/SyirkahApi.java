package tech.kayys.numou.numourest;

import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.numouapplication.StaffContractService;
import tech.kayys.numou.numouapplication.port.TradingRecordPort;
import tech.kayys.numou.numouapplication.SyirkahApplicationService;
import tech.kayys.numou.numouapplication.command.Commands;
import tech.kayys.numou.numouapplication.governance.GovernedSyirkahService;
import tech.kayys.numou.numouapplication.ops.SyirkahIntegrityAuditor;
import tech.kayys.numou.numouapplication.port.SyirkahCatalogPort;
import tech.kayys.numou.numouapplication.query.SyirkahSummary;
import tech.kayys.numou.numoudomain.*;
import tech.kayys.numou.numourest.SyirkahDtos.*;

import java.util.*;

/**
 * Transport-independent façade behind the REST resource. All tenant and actor information comes from the verified
 * request context, never from the payload. Operations that change what the partners agreed (contract revision,
 * correction of a close, liquidation) never execute directly: they create a pending approval for a different person
 * to decide.
 */
public class SyirkahApi {

    static final int DEFAULT_PAGE = 50;
    static final int MAX_PAGE = 200;

    private final SyirkahApplicationService service;
    private final GovernedSyirkahService governed;
    private final SyirkahIntegrityAuditor auditor;
    private final SyirkahCatalogPort catalog;
    private final RequestContextHolder context;
    private final StaffContractService staff;
    private final TradingRecordPort tradingRecords;
    private final java.time.Clock clock = java.time.Clock.systemUTC();

    public SyirkahApi(SyirkahApplicationService service, GovernedSyirkahService governed,
                      SyirkahIntegrityAuditor auditor, SyirkahCatalogPort catalog, RequestContextHolder context) {
        this(service, governed, auditor, catalog, context, null);
    }

    public SyirkahApi(SyirkahApplicationService service, GovernedSyirkahService governed,
                      SyirkahIntegrityAuditor auditor, SyirkahCatalogPort catalog, RequestContextHolder context,
                      StaffContractService staff) {
        this(service, governed, auditor, catalog, context, staff, null);
    }

    public SyirkahApi(SyirkahApplicationService service, GovernedSyirkahService governed,
                      SyirkahIntegrityAuditor auditor, SyirkahCatalogPort catalog, RequestContextHolder context,
                      StaffContractService staff, TradingRecordPort tradingRecords) {
        this.staff = staff;
        this.tradingRecords = tradingRecords;
        this.service = Objects.requireNonNull(service);
        this.governed = Objects.requireNonNull(governed);
        this.auditor = Objects.requireNonNull(auditor);
        this.catalog = Objects.requireNonNull(catalog);
        this.context = Objects.requireNonNull(context);
    }

    // ------------------------------------------------------------ identity

    /** Only a tenant bound to the authenticated principal is accepted; a header-supplied one is not trusted. */
    TenantId tenant() {
        if (!context.tenantVerified() || context.tenantId() == null) {
            throw new SecurityException("No verified tenant on this request");
        }
        try {
            return new TenantId(UUID.fromString(context.tenantId()));
        } catch (IllegalArgumentException e) {
            throw new SecurityException("Verified tenant is not a valid id");
        }
    }

    String actor() {
        String a = context.actor();
        if (a == null || a.isBlank()) {
            throw new SecurityException("No authenticated actor on this request");
        }
        return a;
    }

    private static SyirkahId sid(String s) {
        return new SyirkahId(SyirkahDtos.uuid(s, "syirkahId"));
    }

    private static CloseId cid(String s, String field) {
        return new CloseId(SyirkahDtos.uuid(s, field));
    }

    private static CloseId cidOrNew(String s, String field) {
        return s == null || s.isBlank() ? CloseId.generate() : cid(s, field);
    }

    private Syirkah load(String id) {
        return service.get(tenant(), sid(id));
    }

    // ------------------------------------------------------------ queries

    public Page list(String status, String cursor, Integer limit) {
        int n = limit == null ? DEFAULT_PAGE : limit;
        if (n < 1 || n > MAX_PAGE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE);
        }
        Optional<SyirkahStatus> st = status == null || status.isBlank() ? Optional.empty()
                : Optional.of(SyirkahDtos.enumOf(SyirkahStatus.class, status, "status"));
        Optional<SyirkahId> after = cursor == null || cursor.isBlank() ? Optional.empty() : Optional.of(sid(cursor));
        List<SyirkahSummary> rows = catalog.page(tenant(), st, after, n + 1);
        boolean more = rows.size() > n;
        List<SyirkahSummary> items = more ? rows.subList(0, n) : rows;
        String next = more ? items.get(items.size() - 1).id().value().toString() : null;
        return new Page(items.stream().map(SyirkahDtos::summary).toList(), next);
    }

    public SyirkahView get(String id) {
        return SyirkahDtos.syirkah(load(id));
    }

    public List<CloseView> closes(String id) {
        return load(id).closes().stream().map(SyirkahDtos::close).toList();
    }

    public ReconciliationView reconciliation(String id) {
        EquityReconciliation r = load(id).reconcile();
        return new ReconciliationView(r.balanced(), SyirkahDtos.amt(r.partnerEquity()),
                SyirkahDtos.amt(r.netCashContributed()), SyirkahDtos.amt(r.recognizedResult()),
                SyirkahDtos.amt(r.difference()));
    }

    public IntegrityView integrity(String id) {
        List<String> problems = load(id).verifyIntegrity();
        return new IntegrityView(problems.isEmpty(), problems);
    }

    /** CSV of one partner's statement. */
    public String statementCsv(String id, String partnerId) {
        Syirkah s = load(id);
        return CsvExport.statement(s.statement(SyirkahDtos.partner(partnerId, "partnerId")));
    }

    public String closeReportCsv(String id, String closeId) {
        return CsvExport.closeReport(load(id).closeReport(cid(closeId, "closeId")));
    }

    public BalanceSheetView balanceSheet(String id, BalanceSheetRequest r) {
        Syirkah s = load(id);
        Currency cur = s.contract().currency();
        List<BookStatements.AssetLine> assets = new ArrayList<>();
        for (StatementLineDto l : r.assets() == null ? List.<StatementLineDto>of() : r.assets()) {
            assets.add(new BookStatements.AssetLine(SyirkahDtos.enumOf(BookStatements.AssetGroup.class, l.group(),
                    "assets.group"), SyirkahDtos.required(l.label(), "assets.label"),
                    SyirkahDtos.money(l.amount(), cur, "assets.amount")));
        }
        List<BookStatements.LiabilityLine> liabilities = new ArrayList<>();
        for (StatementLineDto l : r.liabilities() == null ? List.<StatementLineDto>of() : r.liabilities()) {
            liabilities.add(new BookStatements.LiabilityLine(SyirkahDtos.enumOf(BookStatements.LiabilityGroup.class,
                    l.group(), "liabilities.group"), SyirkahDtos.required(l.label(), "liabilities.label"),
                    SyirkahDtos.money(l.amount(), cur, "liabilities.amount")));
        }
        return SyirkahDtos.balanceSheet(BookStatements.balanceSheet(s, statementName(r.name(), id),
                SyirkahDtos.required(r.asOf(), "asOf"), assets, liabilities));
    }

    public IncomeStatementView incomeStatement(String id, String closeId, String name) {
        return SyirkahDtos.incomeStatement(BookStatements.incomeStatement(statementName(name, id),
                load(id).close(cid(closeId, "closeId"))));
    }

    private static String statementName(String name, String id) {
        return name == null || name.isBlank() ? "Syirkah " + id : name;
    }

    public List<LifecycleRecordView> lifecycleRecords(String id) {
        return service.lifecycleRecords(tenant(), sid(id)).stream().map(SyirkahDtos::lifecycleRecord).toList();
    }

    public StaffView engageStaff(String id, StaffRequest r) {
        if (staff == null) {
            throw new IllegalStateException("staff contracts are not configured");
        }
        Syirkah s = load(id);
        int seq = staff.engage(tenant(), sid(id), SyirkahDtos.staff(SyirkahDtos.required(r, "body"),
                s.contract().currency()));
        return staffView(id, seq);
    }

    public List<StaffView> staffContracts(String id) {
        if (staff == null) {
            throw new IllegalStateException("staff contracts are not configured");
        }
        return staff.list(tenant(), sid(id)).stream().map(SyirkahDtos::staffView).toList();
    }

    private StaffView staffView(String id, int seq) {
        return staffContracts(id).stream().filter(v -> v.sequence() == seq).findFirst().orElseThrow();
    }

    public AuditView audit() {
        SyirkahIntegrityAuditor.Report r = auditor.audit(tenant());
        return new AuditView(r.checked(), r.ok(), r.findings().stream()
                .map(f -> new AuditFindingView(f.id().value().toString(), f.problems())).toList());
    }

    /** The Sharia rules this build enforces. Static, the same for every tenant. */
    public RulesView rules() {
        java.util.List<ShariaRule> all = ShariaRuleCatalog.all();
        return new RulesView(all.size(),
                (int) all.stream().filter(r -> r.ruling() == ShariaRule.Ruling.PENDING_ADVISER).count(),
                ShariaRuleCatalog.FROZEN_FINGERPRINT != null, ShariaRuleCatalog.ADVISER_SIGNOFF,
                ShariaRuleCatalog.fingerprint(),
                all.stream().map(r -> new RuleView(r.id(), r.title(), r.source(), r.enforcement().name(),
                        r.ruling().name(), r.codes(), r.testedBy(), r.note())).toList());
    }

    public String rulesMarkdown() {
        return ShariaRuleCatalog.render();
    }

    // ------------------------------------------------------------ direct commands

    public SyirkahView open(OpenRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
        SyirkahId id = r.syirkahId() == null || r.syirkahId().isBlank() ? SyirkahId.generate() : sid(r.syirkahId());
        Commands.Open command = new Commands.Open(tenant(), id, SyirkahDtos.contract(r.contract()),
                r.amountScale());
        return SyirkahDtos.syirkah(r.formation() == null
                ? service.open(command)
                : service.openWithFormation(command, SyirkahDtos.formation(r.formation()),
                        SyirkahDtos.policy(r.formationPolicy())));
    }

    public CloseView closePeriod(String id, ClosePeriodRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Currency currency = load(id).contract().currency();
        if (r.trading() != null && r.result() != null) {
            throw new IllegalArgumentException("give either result or trading, not both");
        }
        FinancialResult figures = r.trading() != null ? SyirkahDtos.trading(r.trading(), currency)
                : SyirkahDtos.result(r.result(), currency);
        CloseId closeId = cidOrNew(r.closeId(), "closeId");
        CloseView view = SyirkahDtos.close(service.closePeriod(new Commands.ClosePeriod(tenant(), sid(id),
                closeId, r.periodStart(), r.periodEnd(),
                figures, SyirkahDtos.fault(r.fault(), currency),
                SyirkahDtos.enumOf(ProfitDisposition.class, r.disposition(), "disposition"))));
        if (r.trading() != null && tradingRecords != null) {
            // written right after the close; a failure here leaves the close in place without its working
            tradingRecords.record(tenant(), sid(id), closeId, SyirkahDtos.tradingInput(r.trading(), currency),
                    clock.instant());
        }
        return view;
    }

    /** The trading figures a close was worked out from, if it was closed from trading figures. */
    public TradingDto closeTrading(String id, String closeId) {
        if (tradingRecords == null) {
            throw new IllegalStateException("trading records are not configured");
        }
        return tradingRecords.find(tenant(), sid(id), cid(closeId, "closeId"))
                .map(x -> SyirkahDtos.tradingView(x.input()))
                .orElseThrow(() -> new SyirkahNotFoundException("no trading figures for close " + closeId));
    }

    public SyirkahView distribute(String id, PaymentRequest r) {
        payment(r);
        Currency currency = load(id).contract().currency();
        service.distributeProfit(new Commands.DistributeProfit(tenant(), sid(id),
                SyirkahDtos.partner(r.partnerId(), "partnerId"), SyirkahDtos.money(r.amount(), currency, "amount"),
                r.reference()));
        return get(id);
    }

    public SyirkahView settle(String id, PaymentRequest r) {
        payment(r);
        Currency currency = load(id).contract().currency();
        service.settleLiability(new Commands.SettleLiability(tenant(), sid(id),
                SyirkahDtos.partner(r.partnerId(), "partnerId"), SyirkahDtos.money(r.amount(), currency, "amount"),
                r.reference()));
        return get(id);
    }

    private static void payment(PaymentRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    // ------------------------------------------------------------ governed commands (maker side)

    public ApprovalView proposeRevision(String id, ReviseRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Set<PartnerId> approvals = new TreeSet<>();
        for (String p : r.approvals() == null ? List.<String>of() : r.approvals()) {
            approvals.add(SyirkahDtos.partner(p, "approvals"));
        }
        return SyirkahDtos.approval(governed.proposeRevision(new Commands.ReviseContract(tenant(), sid(id),
                SyirkahDtos.contract(r.next()), approvals,
                r.lifecycle() == null ? null : SyirkahDtos.lifecycle(r.lifecycle())), actor()));
    }

    public ApprovalView proposeCorrection(String id, CorrectCloseRequest r) {
        if (r == null) {
            throw new IllegalArgumentException("request body is required");
        }
        Syirkah s = load(id);
        CloseId target = cid(r.targetCloseId(), "targetCloseId");
        CloseSnapshot t = s.close(target); // throws a rule violation for an unknown close
        int affected = (int) s.closes().stream()
                .filter(c -> c.status() == CloseStatus.ACTIVE && c.sequence() >= t.sequence()).count();
        List<CloseId> ids = new ArrayList<>();
        if (r.replacementCloseIds() == null || r.replacementCloseIds().isEmpty()) {
            for (int i = 0; i < affected; i++) {
                ids.add(CloseId.generate());
            }
        } else {
            if (r.replacementCloseIds().size() != affected) {
                throw new IllegalArgumentException("expected " + affected + " replacement close ids (one per "
                        + "effective close from the target onwards) but got " + r.replacementCloseIds().size());
            }
            r.replacementCloseIds().forEach(x -> ids.add(cid(x, "replacementCloseIds")));
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("the target close is not an effective close");
        }
        Currency currency = s.contract().currency();
        return SyirkahDtos.approval(governed.proposeCorrection(new Commands.CorrectClose(tenant(), sid(id), target,
                ids.get(0), SyirkahDtos.result(r.corrected(), currency), SyirkahDtos.fault(r.fault(), currency),
                SyirkahDtos.enumOf(ProfitDisposition.class, r.disposition(), "disposition"),
                ids.subList(1, ids.size())), actor()));
    }

    public ApprovalView proposeStartLiquidation(String id) {
        return SyirkahDtos.approval(governed.proposeStartLiquidation(
                new Commands.StartLiquidation(tenant(), sid(id)), actor()));
    }

    public ApprovalView proposeLiquidation(String id, LiquidateRequest r) {
        Currency currency = load(id).contract().currency();
        return SyirkahDtos.approval(governed.proposeLiquidation(new Commands.Liquidate(tenant(), sid(id),
                SyirkahDtos.liquidation(r, currency), SyirkahDtos.fault(r.fault(), currency)), actor()));
    }

    // ------------------------------------------------------------ approvals (checker side)

    public List<ApprovalView> pending() {
        return governed.pending(tenant()).stream().map(SyirkahDtos::approval).toList();
    }

    public ApprovalView approve(String approvalId) {
        return SyirkahDtos.approval(governed.approve(tenant(), SyirkahDtos.uuid(approvalId, "approvalId"), actor()));
    }

    public ApprovalView reject(String approvalId, RejectRequest r) {
        String reason = r == null ? null : r.reason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a reason is required to reject");
        }
        return SyirkahDtos.approval(governed.reject(tenant(), SyirkahDtos.uuid(approvalId, "approvalId"), actor(),
                reason));
    }

}
