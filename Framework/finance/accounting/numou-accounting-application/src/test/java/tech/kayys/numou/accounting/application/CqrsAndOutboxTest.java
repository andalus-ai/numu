package tech.kayys.syirkah.accounting.application;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.api.command.*;
import tech.kayys.syirkah.accounting.application.api.query.GetTrialBalanceQuery;
import tech.kayys.syirkah.accounting.application.compliance.ComplianceRulePipeline;
import tech.kayys.syirkah.accounting.application.cqrs.*;
import tech.kayys.syirkah.accounting.application.handler.*;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.*;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.application.projection.TrialBalanceProjection;
import tech.kayys.syirkah.accounting.application.service.AccountingComplianceEngine;
import tech.kayys.syirkah.accounting.application.service.FinancialReportingService;
import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.identifier.*;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.*;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.report.TrialBalance;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class CqrsAndOutboxTest {

    private final Currency usd = Currency.of("USD");
    private final TenantRef tenant = TenantRef.of("acme-corp");
    private final LedgerId ledger = LedgerId.primary();

    private final Map<AccountId, Account> accounts = new ConcurrentHashMap<>();
    private final Map<JournalEntryId, JournalEntry> journals = new ConcurrentHashMap<>();
    private final List<OutboxEvent> outboxList = new ArrayList<>();

    private AccountRepository accountRepo;
    private JournalEntryRepository journalRepo;
    private FiscalPeriodRepository periodRepo;
    private OutboxRepository outboxRepo;
    private ProjectionRegistry projectionRegistry;
    private TrialBalanceProjection tbProjection;

    private CommandBus commandBus;
    private QueryBus queryBus;

    @BeforeEach
    void setup() {
        accounts.clear();
        journals.clear();
        outboxList.clear();

        accountRepo = new AccountRepository() {
            @Override public Uni<Optional<Account>> findById(AccountId id) { return Uni.createFrom().item(Optional.ofNullable(accounts.get(id))); }
            @Override public Uni<Optional<Account>> findByAccountNumber(String num) { return Uni.createFrom().item(accounts.values().stream().filter(a -> a.getAccountNumber().equals(num)).findFirst()); }
            @Override public Uni<List<Account>> findAll() { return Uni.createFrom().item(new ArrayList<>(accounts.values())); }
            @Override public Uni<Void> save(Account a) { accounts.put(a.id(), a); return Uni.createFrom().voidItem(); }
        };

        journalRepo = new JournalEntryRepository() {
            @Override public Uni<Optional<JournalEntry>> findById(JournalEntryId id) { return Uni.createFrom().item(Optional.ofNullable(journals.get(id))); }
            @Override public Uni<List<JournalEntry>> findAll() { return Uni.createFrom().item(new ArrayList<>(journals.values())); }
            @Override public Uni<Void> save(JournalEntry je) { journals.put(je.id(), je); return Uni.createFrom().voidItem(); }
        };

        periodRepo = new FiscalPeriodRepository() {
            @Override public Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id) { return Uni.createFrom().item(Optional.empty()); }
            @Override public Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date) {
                return Uni.createFrom().item(Optional.of(new FiscalPeriod(FiscalPeriodId.generate(), 1, "2026-01", LocalDate.now().minusDays(1), LocalDate.now().plusDays(20))));
            }
            @Override public Uni<List<FiscalPeriod>> findAll() { return Uni.createFrom().item(Collections.emptyList()); }
            @Override public Uni<Void> save(FiscalPeriod p) { return Uni.createFrom().voidItem(); }
        };

        outboxRepo = new OutboxRepository() {
            @Override public Uni<Void> save(OutboxEvent event) { outboxList.add(event); return Uni.createFrom().voidItem(); }
            @Override public Uni<List<OutboxEvent>> findUnprocessed(int limit) { return Uni.createFrom().item(outboxList.stream().filter(e -> !e.isProcessed()).limit(limit).toList()); }
            @Override public Uni<Void> markProcessed(UUID id) { return Uni.createFrom().voidItem(); }
        };

        projectionRegistry = new ProjectionRegistry();
        tbProjection = new TrialBalanceProjection();
        projectionRegistry.register(tbProjection);

        commandBus = new DefaultCommandBus();
        queryBus = new DefaultQueryBus();

        AccountingComplianceEngine engine = new AccountingComplianceEngine(ComplianceConfiguration.ifrsDefault());
        ComplianceRulePipeline pipeline = new ComplianceRulePipeline();

        commandBus.registerHandler(PostJournalEntryCommand.class, new PostJournalEntryHandler(journalRepo, accountRepo, periodRepo, outboxRepo, projectionRegistry, engine, pipeline));
        commandBus.registerHandler(CreateAccountCommand.class, new CreateAccountHandler(accountRepo, outboxRepo));
        commandBus.registerHandler(ReverseJournalEntryCommand.class, new ReverseJournalEntryHandler(journalRepo, accountRepo, outboxRepo, projectionRegistry));

        FinancialReportingService reportingService = new FinancialReportingService(accountRepo);
        queryBus.registerHandler(GetTrialBalanceQuery.class, new ReportingQueryHandlers.TrialBalanceHandler(reportingService));
    }

    @Test
    @DisplayName("CQRS Bus: CreateAccountCommand dispatches and emits Outbox event")
    void testCreateAccountViaCommandBus() {
        CreateAccountCommand cmd = new CreateAccountCommand(tenant, ledger, "1010", "Petty Cash", "Office cash", AccountType.ASSET, "USD");
        AccountId id = commandBus.<CreateAccountCommand, AccountId>dispatch(cmd).await().atMost(Duration.ofSeconds(5));

        assertNotNull(id);
        assertTrue(accounts.containsKey(id));
        assertEquals("Petty Cash", accounts.get(id).getName());

        // Verify outbox record
        assertEquals(1, outboxList.size());
        assertEquals("Account", outboxList.get(0).aggregateType());
        assertEquals("AccountCreated", outboxList.get(0).eventType());
    }

    @Test
    @DisplayName("CQRS Bus: PostJournalEntryCommand dispatches, updates balances, and creates Outbox events")
    void testPostJournalEntryViaCommandBus() {
        AccountId bankId = AccountId.generate();
        Account bank = new Account(bankId, tenant, ledger, "1002", "Bank", AccountType.ASSET, usd);
        accounts.put(bankId, bank);

        AccountId revId = AccountId.generate();
        Account rev = new Account(revId, tenant, ledger, "4001", "Revenue", AccountType.REVENUE, usd);
        accounts.put(revId, rev);

        PostJournalEntryCommand cmd = PostJournalEntryCommand.builder()
                .description("Service Income")
                .referenceNumber("INV-999")
                .lines(List.of(
                        new PostJournalEntryCommand.JournalLineCommand(bankId.value(), "DEBIT", "1200.00", "USD", "Bank debit"),
                        new PostJournalEntryCommand.JournalLineCommand(revId.value(), "CREDIT", "1200.00", "USD", "Revenue credit")
                ))
                .build();

        JournalEntryId jeId = commandBus.<PostJournalEntryCommand, JournalEntryId>dispatch(cmd).await().atMost(Duration.ofSeconds(5));
        assertNotNull(jeId);
        assertTrue(journals.containsKey(jeId));
        assertEquals(JournalEntry.EntryStatus.POSTED, journals.get(jeId).getStatus());

        // Account balances updated
        assertEquals(Money.of(new java.math.BigDecimal("1200.00"), usd), accounts.get(bankId).getCurrentBalance());
        assertEquals(Money.of(new java.math.BigDecimal("1200.00"), usd), accounts.get(revId).getCurrentBalance());

        // Outbox event written
        assertTrue(outboxList.stream().anyMatch(e -> e.eventType().equals("JournalEntryPosted")));

        // Query Trial Balance via QueryBus
        TrialBalance tb = queryBus.<GetTrialBalanceQuery, TrialBalance>execute(new GetTrialBalanceQuery(tenant, ledger, LocalDate.now()))
                .await().atMost(Duration.ofSeconds(5));
        assertNotNull(tb);
        assertTrue(tb.balanced());
        assertEquals(Money.of(new java.math.BigDecimal("1200.00"), usd), tb.totalDebit());
        assertEquals(Money.of(new java.math.BigDecimal("1200.00"), usd), tb.totalCredit());
    }
}
