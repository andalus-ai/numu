package tech.kayys.syirkah.accounting.interfaces.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.syirkah.accounting.application.ap.ApInvoiceService;
import tech.kayys.syirkah.accounting.application.api.command.*;
import tech.kayys.syirkah.accounting.application.api.query.*;
import tech.kayys.syirkah.accounting.application.ar.ArInvoiceService;
import tech.kayys.syirkah.accounting.receivables.application.InvoiceService;
import tech.kayys.syirkah.accounting.receivables.application.PaymentService;
import tech.kayys.syirkah.accounting.receivables.repository.InvoiceRepository;
import tech.kayys.syirkah.accounting.receivables.repository.PaymentRepository;
import tech.kayys.syirkah.budget.adapter.memory.InMemoryActualRepository;
import tech.kayys.syirkah.budget.adapter.memory.InMemoryBudgetRepository;
import tech.kayys.syirkah.budget.adapter.memory.InMemoryBudgetStore;
import tech.kayys.syirkah.budget.adapter.memory.InMemoryCommitmentRepository;
import tech.kayys.syirkah.budget.application.BudgetService;
import tech.kayys.syirkah.budget.application.BudgetingService;
import tech.kayys.syirkah.accounting.application.compliance.ComplianceRulePipeline;
import tech.kayys.syirkah.accounting.application.cost.CostAccountingService;
import tech.kayys.syirkah.accounting.application.cqrs.*;
import tech.kayys.syirkah.accounting.application.document.DocumentService;
import tech.kayys.syirkah.accounting.application.document.DocumentStorage;
import tech.kayys.syirkah.accounting.application.document.InMemoryDocumentStorage;
import tech.kayys.syirkah.accounting.application.handler.*;
import tech.kayys.syirkah.accounting.application.hardening.AuditTrailService;
import tech.kayys.syirkah.accounting.application.hardening.IdempotencyGuard;
import tech.kayys.syirkah.accounting.application.hardening.SoDEnforcer;
import tech.kayys.syirkah.accounting.application.mdm.MasterDataService;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.port.FixedAssetRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.project.adapter.memory.InMemoryProjectFinanceStore;
import tech.kayys.syirkah.project.adapter.memory.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.project.ProjectFinanceService;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.application.projection.TrialBalanceProjection;
import tech.kayys.syirkah.accounting.application.rule.DeclarativeRuleEngine;
import tech.kayys.syirkah.accounting.application.service.*;
import tech.kayys.syirkah.accounting.application.tax.TaxService;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.GamelanWorkflowClient;
import tech.kayys.syirkah.accounting.application.workflow.gamelan.InMemoryGamelanWorkflowAdapter;
import tech.kayys.syirkah.accounting.application.workflow.WorkflowEngine;
import tech.kayys.syirkah.accounting.application.close.CloseValidationEngine;
import tech.kayys.syirkah.accounting.application.close.FinancialCloseService;
import tech.kayys.syirkah.accounting.application.close.PeriodLockEngine;
import tech.kayys.syirkah.accounting.application.fund.FundAccountingService;
import tech.kayys.syirkah.accounting.application.islamic.IslamicFinanceService;
import tech.kayys.syirkah.accounting.application.islamic.ZakatEngine;
import tech.kayys.syirkah.accounting.application.quality.QualityService;
import tech.kayys.syirkah.accounting.application.maintenance.MaintenanceService;
import tech.kayys.syirkah.accounting.application.legal.LegalService;
import tech.kayys.syirkah.accounting.application.risk.RiskService;
import tech.kayys.syirkah.accounting.application.audit.InternalAuditService;
import tech.kayys.syirkah.accounting.consolidation.*;
import tech.kayys.syirkah.accounting.payables.infrastructure.InMemoryVendorInvoiceRepository;
import tech.kayys.syirkah.accounting.receivables.infrastructure.InMemoryCustomerInvoiceRepository;
import tech.kayys.syirkah.inventory.adapter.memory.InMemoryInventoryRepository;
import tech.kayys.syirkah.inventory.application.InventoryService;

@ApplicationScoped
public class AccountingServiceProducer {

    @Produces
    @ApplicationScoped
    public AccountingComplianceEngine complianceEngine() {
        return new AccountingComplianceEngine();
    }

    @Produces
    @ApplicationScoped
    public ComplianceRulePipeline complianceRulePipeline() {
        return new ComplianceRulePipeline();
    }

    @Produces
    @ApplicationScoped
    public ProjectionRegistry projectionRegistry() {
        ProjectionRegistry registry = new ProjectionRegistry();
        registry.register(new TrialBalanceProjection());
        return registry;
    }

    @Produces
    @ApplicationScoped
    public CommandBus commandBus(
            JournalEntryRepository journalRepo,
            AccountRepository accountRepo,
            FiscalPeriodRepository periodRepo,
            OutboxRepository outboxRepo,
            ProjectionRegistry projectionRegistry,
            AccountingComplianceEngine complianceEngine,
            ComplianceRulePipeline rulePipeline
    ) {
        DefaultCommandBus bus = new DefaultCommandBus();
        bus.registerHandler(PostJournalEntryCommand.class, new PostJournalEntryHandler(journalRepo, accountRepo, periodRepo, outboxRepo, projectionRegistry, complianceEngine, rulePipeline));
        bus.registerHandler(ApproveJournalEntryCommand.class, new ApproveJournalEntryHandler(journalRepo, outboxRepo));
        bus.registerHandler(ReverseJournalEntryCommand.class, new ReverseJournalEntryHandler(journalRepo, accountRepo, outboxRepo, projectionRegistry));
        bus.registerHandler(CreateAccountCommand.class, new CreateAccountHandler(accountRepo, outboxRepo));
        return bus;
    }

    @Produces
    @ApplicationScoped
    public QueryBus queryBus(AccountRepository accountRepo) {
        DefaultQueryBus bus = new DefaultQueryBus();
        FinancialReportingService reporting = new FinancialReportingService(accountRepo);
        bus.registerHandler(GetTrialBalanceQuery.class, new ReportingQueryHandlers.TrialBalanceHandler(reporting));
        bus.registerHandler(GetBalanceSheetQuery.class, new ReportingQueryHandlers.BalanceSheetHandler(reporting));
        bus.registerHandler(GetIncomeStatementQuery.class, new ReportingQueryHandlers.IncomeStatementHandler(reporting));
        bus.registerHandler(GetCashFlowQuery.class, new ReportingQueryHandlers.CashFlowHandler(reporting));
        return bus;
    }

    @Produces
    @ApplicationScoped
    public GeneralLedgerService generalLedgerService(
            AccountRepository accountRepo,
            JournalEntryRepository journalRepo,
            FiscalPeriodRepository periodRepo,
            AccountingComplianceEngine complianceEngine
    ) {
        return new GeneralLedgerService(accountRepo, journalRepo, periodRepo, complianceEngine);
    }

    @Produces
    @ApplicationScoped
    public JournalEntryReversalService reversalService(
            JournalEntryRepository journalRepo,
            AccountRepository accountRepo
    ) {
        return new JournalEntryReversalService(journalRepo, accountRepo);
    }

    @Produces
    @ApplicationScoped
    public FiscalPeriodService fiscalPeriodService(
            FiscalPeriodRepository periodRepo,
            AccountRepository accountRepo,
            JournalEntryRepository journalRepo
    ) {
        return new FiscalPeriodService(periodRepo, accountRepo, journalRepo);
    }

    @Produces
    @ApplicationScoped
    public AssetDepreciationService depreciationService(
            FixedAssetRepository assetRepo,
            JournalEntryRepository journalRepo
    ) {
        return new AssetDepreciationService(assetRepo, journalRepo);
    }

    @Produces
    @ApplicationScoped
    public FinancialReportingService reportingService(AccountRepository accountRepo) {
        return new FinancialReportingService(accountRepo);
    }

    // --- Accounts Payable & Receivable ---

    @Produces
    @ApplicationScoped
    public InMemoryVendorInvoiceRepository vendorInvoiceRepository() {
        return new InMemoryVendorInvoiceRepository();
    }

    @Produces
    @ApplicationScoped
    public ApInvoiceService apInvoiceService(InMemoryVendorInvoiceRepository vendorRepo) {
        return new ApInvoiceService(vendorRepo);
    }

    @Produces
    @ApplicationScoped
    public InMemoryCustomerInvoiceRepository customerInvoiceRepository() {
        return new InMemoryCustomerInvoiceRepository();
    }

    @Produces
    @ApplicationScoped
    public ArInvoiceService arInvoiceService(InMemoryCustomerInvoiceRepository invoiceRepo) {
        return new ArInvoiceService(invoiceRepo);
    }

    @Produces
    @ApplicationScoped
    public InvoiceService receivablesInvoiceService(InvoiceRepository invoiceRepo) {
        return new InvoiceService(invoiceRepo);
    }

    @Produces
    @ApplicationScoped
    public PaymentService receivablesPaymentService(InvoiceRepository invoiceRepo, PaymentRepository paymentRepo) {
        return new PaymentService(invoiceRepo, paymentRepo);
    }

    // --- Workflow & Rules Engines ---

    @Produces
    @ApplicationScoped
    public WorkflowEngine workflowEngine() {
        return new WorkflowEngine();
    }

    @Produces
    @ApplicationScoped
    public GamelanWorkflowClient gamelanWorkflowClient() {
        return new InMemoryGamelanWorkflowAdapter();
    }

    @Produces
    @ApplicationScoped
    public DeclarativeRuleEngine declarativeRuleEngine() {
        return new DeclarativeRuleEngine();
    }

    // --- Hardening & Governance ---

    @Produces
    @ApplicationScoped
    public IdempotencyGuard idempotencyGuard() {
        return new IdempotencyGuard();
    }

    @Produces
    @ApplicationScoped
    public SoDEnforcer sodEnforcer() {
        return new SoDEnforcer();
    }

    @Produces
    @ApplicationScoped
    public AuditTrailService auditTrailService() {
        return new AuditTrailService();
    }

    // --- Strategic Business Platforms ---

    @Produces
    @ApplicationScoped
    public DocumentStorage documentStorage() {
        return new InMemoryDocumentStorage();
    }

    @Produces
    @ApplicationScoped
    public DocumentService documentService(DocumentStorage storage) {
        return new DocumentService(storage);
    }

    @Produces
    @ApplicationScoped
    public MasterDataService masterDataService() {
        return new MasterDataService();
    }

    @Produces
    @ApplicationScoped
    public InventoryService inventoryService() {
        return new InventoryService(new InMemoryInventoryRepository());
    }

    @Produces
    @ApplicationScoped
    public BudgetService budgetService() {
        return new BudgetService(new InMemoryBudgetStore());
    }

    @Produces
    @ApplicationScoped
    public BudgetingService budgetingService() {
        return new BudgetingService(new InMemoryBudgetRepository(),
                new InMemoryCommitmentRepository(), new InMemoryActualRepository());
    }

    @Produces
    @ApplicationScoped
    public CostAccountingService costAccountingService() {
        return new CostAccountingService();
    }

    @Produces
    @ApplicationScoped
    public ProjectFinanceService projectFinanceService() {
        return new ProjectFinanceService(
                new InMemoryProjectRepository(),
                new InMemoryProjectFinanceStore()
        );
    }

    @Produces
    @ApplicationScoped
    public TaxService taxService() {
        return new TaxService();
    }

    // --- Financial Close Orchestration ---

    @Produces
    @ApplicationScoped
    public CloseValidationEngine closeValidationEngine() {
        return new CloseValidationEngine();
    }

    @Produces
    @ApplicationScoped
    public PeriodLockEngine periodLockEngine() {
        return new PeriodLockEngine();
    }

    @Produces
    @ApplicationScoped
    public FinancialCloseService financialCloseService(CloseValidationEngine validationEngine, PeriodLockEngine lockEngine) {
        return new FinancialCloseService(validationEngine, lockEngine);
    }

    // --- Fund & Grant Accounting ---

    @Produces
    @ApplicationScoped
    public FundAccountingService fundAccountingService() {
        return new FundAccountingService();
    }

    // --- Islamic Finance & Zakat ---

    @Produces
    @ApplicationScoped
    public ZakatEngine zakatEngine() {
        return new ZakatEngine();
    }

    @Produces
    @ApplicationScoped
    public IslamicFinanceService islamicFinanceService(ZakatEngine zakatEngine) {
        return new IslamicFinanceService(zakatEngine);
    }

    // --- Advanced Multi-Entity Consolidation ---

    @Produces
    @ApplicationScoped
    public OwnershipEngine ownershipEngine() {
        return new OwnershipEngine();
    }

    @Produces
    @ApplicationScoped
    public TranslationEngine translationEngine() {
        return new TranslationEngine();
    }

    @Produces
    @ApplicationScoped
    public MinorityInterestEngine minorityInterestEngine() {
        return new MinorityInterestEngine();
    }

    @Produces
    @ApplicationScoped
    public ConsolidationService consolidationService() {
        return new ConsolidationService();
    }

    // --- Quality, CAPA & Maintenance ---

    @Produces
    @ApplicationScoped
    public QualityService qualityService() {
        return new QualityService();
    }

    @Produces
    @ApplicationScoped
    public MaintenanceService maintenanceService() {
        return new MaintenanceService();
    }

    // --- Legal & Risk ---

    @Produces
    @ApplicationScoped
    public LegalService legalService() {
        return new LegalService();
    }

    @Produces
    @ApplicationScoped
    public RiskService riskService() {
        return new RiskService();
    }

    // --- Internal Audit ---

    @Produces
    @ApplicationScoped
    public InternalAuditService internalAuditService() {
        return new InternalAuditService();
    }
}
