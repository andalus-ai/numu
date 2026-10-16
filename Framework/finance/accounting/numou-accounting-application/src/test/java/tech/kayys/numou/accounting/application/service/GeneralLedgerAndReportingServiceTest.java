package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.port.*;
import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.identifier.*;
import tech.kayys.syirkah.accounting.domain.model.*;
import tech.kayys.syirkah.accounting.domain.report.*;
import tech.kayys.syirkah.accounting.domain.valueobject.*;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class GeneralLedgerAndReportingServiceTest {

    private final Currency usd = Currency.of("USD");

    // In-memory mocks
    private final Map<AccountId, Account> accountStore = new ConcurrentHashMap<>();
    private final Map<JournalEntryId, JournalEntry> journalStore = new ConcurrentHashMap<>();
    private final Map<FiscalPeriodId, FiscalPeriod> periodStore = new ConcurrentHashMap<>();

    private AccountRepository accountRepository;
    private JournalEntryRepository journalEntryRepository;
    private FiscalPeriodRepository fiscalPeriodRepository;
    private AccountingComplianceEngine complianceEngine;

    private GeneralLedgerService glService;
    private JournalEntryReversalService reversalService;
    private FiscalPeriodService fiscalPeriodService;
    private FinancialReportingService reportingService;

    @BeforeEach
    void setUp() {
        accountStore.clear();
        journalStore.clear();
        periodStore.clear();

        accountRepository = new AccountRepository() {
            @Override
            public Uni<Optional<Account>> findById(AccountId id) {
                return Uni.createFrom().item(Optional.ofNullable(accountStore.get(id)));
            }
            @Override
            public Uni<Optional<Account>> findByAccountNumber(String num) {
                return Uni.createFrom().item(accountStore.values().stream().filter(a -> a.getAccountNumber().equalsIgnoreCase(num)).findFirst());
            }
            @Override
            public Uni<List<Account>> findAll() {
                return Uni.createFrom().item(new ArrayList<>(accountStore.values()));
            }
            @Override
            public Uni<Void> save(Account account) {
                accountStore.put(account.id(), account);
                return Uni.createFrom().voidItem();
            }
        };

        journalEntryRepository = new JournalEntryRepository() {
            @Override
            public Uni<Optional<JournalEntry>> findById(JournalEntryId id) {
                return Uni.createFrom().item(Optional.ofNullable(journalStore.get(id)));
            }
            @Override
            public Uni<List<JournalEntry>> findAll() {
                return Uni.createFrom().item(new ArrayList<>(journalStore.values()));
            }
            @Override
            public Uni<Void> save(JournalEntry entry) {
                journalStore.put(entry.id(), entry);
                return Uni.createFrom().voidItem();
            }
        };

        fiscalPeriodRepository = new FiscalPeriodRepository() {
            @Override
            public Uni<Optional<FiscalPeriod>> findById(FiscalPeriodId id) {
                return Uni.createFrom().item(Optional.ofNullable(periodStore.get(id)));
            }
            @Override
            public Uni<Optional<FiscalPeriod>> findActivePeriod(LocalDate date) {
                return Uni.createFrom().item(periodStore.values().stream().filter(p -> p.contains(date)).findFirst());
            }
            @Override
            public Uni<List<FiscalPeriod>> findAll() {
                return Uni.createFrom().item(new ArrayList<>(periodStore.values()));
            }
            @Override
            public Uni<Void> save(FiscalPeriod period) {
                periodStore.put(period.id(), period);
                return Uni.createFrom().voidItem();
            }
        };

        complianceEngine = new AccountingComplianceEngine(ComplianceConfiguration.ifrsDefault());
        glService = new GeneralLedgerService(accountRepository, journalEntryRepository, fiscalPeriodRepository, complianceEngine);
        reversalService = new JournalEntryReversalService(journalEntryRepository, accountRepository);
        fiscalPeriodService = new FiscalPeriodService(fiscalPeriodRepository, accountRepository, journalEntryRepository);
        reportingService = new FinancialReportingService(accountRepository);
    }

    @Test
    @DisplayName("GL Service: Posts journal entry and mutates account balances correctly")
    void testPostJournalEntryMutatesBalances() {
        AccountId cashId = AccountId.generate();
        Account cash = new Account(cashId, "1001", "Cash", AccountType.ASSET, usd);
        accountStore.put(cashId, cash);

        AccountId revId = AccountId.generate();
        Account revenue = new Account(revId, "4001", "Sales Revenue", AccountType.REVENUE, usd);
        accountStore.put(revId, revenue);

        // Active open fiscal period
        FiscalPeriod period = new FiscalPeriod(
                FiscalPeriodId.generate(),
                1, "2026-01",
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(25)
        );
        periodStore.put(period.id(), period);

        PostJournalEntryCommand cmd = PostJournalEntryCommand.builder()
                .description("Customer Cash Sale")
                .referenceNumber("INV-001")
                .lines(List.of(
                        new PostJournalEntryCommand.JournalLineCommand(cashId.value(), "DEBIT", "500.00", "USD", "Cash received"),
                        new PostJournalEntryCommand.JournalLineCommand(revId.value(), "CREDIT", "500.00", "USD", "Sales recognized")
                ))
                .build();

        JournalEntryId jeId = glService.postJournalEntry(cmd).await().atMost(Duration.ofSeconds(5));
        assertNotNull(jeId);

        // Verify account balances
        assertEquals(Money.of(new java.math.BigDecimal("500.00"), usd), accountStore.get(cashId).getCurrentBalance());
        assertEquals(Money.of(new java.math.BigDecimal("500.00"), usd), accountStore.get(revId).getCurrentBalance());
    }

    @Test
    @DisplayName("Reporting Service: Generates balanced Trial Balance")
    void testGenerateTrialBalance() {
        AccountId cashId = AccountId.generate();
        Account cash = new Account(cashId, "1001", "Cash", AccountType.ASSET, usd);
        cash.debit(Money.of(1500L, usd));
        accountStore.put(cashId, cash);

        AccountId eqId = AccountId.generate();
        Account equity = new Account(eqId, "3001", "Capital Equity", AccountType.EQUITY, usd);
        equity.credit(Money.of(1500L, usd));
        accountStore.put(eqId, equity);

        TrialBalance tb = reportingService.generateTrialBalance(LocalDate.now()).await().atMost(Duration.ofSeconds(5));
        assertNotNull(tb);
        assertTrue(tb.balanced());
        assertEquals(Money.of(1500L, usd), tb.totalDebit());
        assertEquals(Money.of(1500L, usd), tb.totalCredit());
    }

    @Test
    @DisplayName("Reversal Service: Storno reversal cleanly restores account balances")
    void testReversalRestoresBalances() {
        AccountId bankId = AccountId.generate();
        Account bank = new Account(bankId, "1002", "Bank", AccountType.ASSET, usd);
        bank.debit(Money.of(1000L, usd));
        accountStore.put(bankId, bank);

        AccountId apId = AccountId.generate();
        Account ap = new Account(apId, "2001", "Accounts Payable", AccountType.LIABILITY, usd);
        ap.credit(Money.of(1000L, usd));
        accountStore.put(apId, ap);

        // Entry to pay AP: Debit AP 400, Credit Bank 400
        JournalEntryId originalId = JournalEntryId.generate();
        JournalEntry original = new JournalEntry(originalId, "PAY-01", java.time.Instant.now(), "Pay AP");
        original.addLine(apId, Money.of(400L, usd), Money.zero(usd), "Debit AP");
        original.addLine(bankId, Money.zero(usd), Money.of(400L, usd), "Credit Bank");
        original.post();

        bank.credit(Money.of(400L, usd)); // bank balance is now 600
        ap.debit(Money.of(400L, usd));     // ap balance is now 600
        journalStore.put(originalId, original);

        assertEquals(Money.of(600L, usd), bank.getCurrentBalance());
        assertEquals(Money.of(600L, usd), ap.getCurrentBalance());

        // Perform Storno reversal
        JournalEntryId revId = reversalService.reverseJournalEntry(originalId, "admin", "Wrong payment").await().atMost(Duration.ofSeconds(5));
        assertNotNull(revId);

        // Balances should be restored to 1000
        assertEquals(Money.of(1000L, usd), bank.getCurrentBalance());
        assertEquals(Money.of(1000L, usd), ap.getCurrentBalance());
        assertEquals(JournalEntry.EntryStatus.REVERSED, journalStore.get(originalId).getStatus());
    }
}
