package tech.kayys.syirkah.accounting.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.compliance.*;
import tech.kayys.syirkah.accounting.domain.identifier.*;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.model.*;
import tech.kayys.syirkah.accounting.domain.valueobject.*;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ComplianceAndAccountingTest {

    private final Currency usd = Currency.of("USD");
    private final Currency idr = Currency.of("IDR");

    @Test
    @DisplayName("Double-entry invariant: Balanced journal entry posts successfully")
    void testDoubleEntryBalancedPostsSuccessfully() {
        AccountId cash = AccountId.generate();
        AccountId revenue = AccountId.generate();

        JournalEntry entry = new JournalEntry(
                JournalEntryId.generate(),
                "JE-001",
                Instant.now(),
                "Sale transaction"
        );

        entry.addLine(cash, Money.of(1000L, usd), Money.zero(usd), "Debit Cash");
        entry.addLine(revenue, Money.zero(usd), Money.of(1000L, usd), "Credit Revenue");

        assertDoesNotThrow(() -> entry.post());
        assertEquals(JournalEntry.EntryStatus.POSTED, entry.getStatus());
    }

    @Test
    @DisplayName("Double-entry invariant: Unbalanced journal entry fails to post")
    void testDoubleEntryUnbalancedFails() {
        AccountId cash = AccountId.generate();
        AccountId revenue = AccountId.generate();

        JournalEntry entry = new JournalEntry(
                JournalEntryId.generate(),
                "JE-002",
                Instant.now(),
                "Unbalanced transaction"
        );

        entry.addLine(cash, Money.of(1000L, usd), Money.zero(usd), "Debit Cash");
        entry.addLine(revenue, Money.zero(usd), Money.of(900L, usd), "Credit Revenue partial");

        IllegalStateException ex = assertThrows(IllegalStateException.class, entry::post);
        assertTrue(ex.getMessage().contains("Double-entry balance violation"));
    }

    @Test
    @DisplayName("Sharia compliance: Riba / Interest accounts are strictly prohibited")
    void testShariaProhibitsRibaAccounts() {
        AccountingComplianceStrategy sharia = new ShariaComplianceStrategy();
        ComplianceConfiguration config = ComplianceConfiguration.shariaDefault();

        Account interestRevenue = new Account(
                AccountId.generate(),
                "4002",
                "Interest Revenue",
                AccountType.REVENUE,
                idr
        );

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                sharia.validateAccount(interestRevenue, config)
        );
        assertTrue(ex1.getMessage().contains("Riba/Interest"));

        Account bungaExpense = new Account(
                AccountId.generate(),
                "5005",
                "Beban Bunga Pinjaman",
                AccountType.EXPENSE,
                idr
        );

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                sharia.validateAccount(bungaExpense, config)
        );
        assertTrue(ex2.getMessage().contains("Riba/Interest"));

        Account murabahahMargin = new Account(
                AccountId.generate(),
                "4100",
                "Pendapatan Margin Murabahah",
                AccountType.REVENUE,
                idr
        );
        assertDoesNotThrow(() -> sharia.validateAccount(murabahahMargin, config));
    }

    @Test
    @DisplayName("Sharia compliance: Transactions must be classified with valid Sharia contract type")
    void testShariaEnforcesContractTagging() {
        AccountingComplianceStrategy sharia = new ShariaComplianceStrategy();
        ComplianceConfiguration config = ComplianceConfiguration.shariaDefault();

        AccountId cash = AccountId.generate();
        AccountId murabahahSales = AccountId.generate();

        JournalEntry unclassified = new JournalEntry(
                JournalEntryId.generate(),
                "JE-SH-001",
                Instant.now(),
                "Unclassified sale"
        );
        unclassified.addLine(cash, Money.of(5000L, idr), Money.zero(idr), "Kas");
        unclassified.addLine(murabahahSales, Money.zero(idr), Money.of(5000L, idr), "Penjualan");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                sharia.validateJournalEntry(unclassified, config)
        );
        assertTrue(ex.getMessage().contains("valid Sharia contract type"));

        // Tag with Murabahah
        unclassified.setShariaContractType(ShariaContractType.MURABAHAH);
        assertDoesNotThrow(() -> sharia.validateJournalEntry(unclassified, config));
    }

    @Test
    @DisplayName("Storno reversal: Creates exact inverse entry swapping debits and credits")
    void testStornoReversalSwapsDebitsAndCredits() {
        AccountId bank = AccountId.generate();
        AccountId accountsPayable = AccountId.generate();

        JournalEntry original = new JournalEntry(
                JournalEntryId.generate(),
                "JE-PAY-01",
                Instant.now(),
                "Vendor Payment"
        );
        original.addLine(accountsPayable, Money.of(2500L, usd), Money.zero(usd), "Debit AP");
        original.addLine(bank, Money.zero(usd), Money.of(2500L, usd), "Credit Bank");
        original.post();

        JournalEntryId revId = JournalEntryId.generate();
        JournalEntry reversal = original.createStornoReversal(
                revId,
                "REV-JE-PAY-01",
                Instant.now(),
                "auditor@company.com",
                "Duplicate payment"
        );

        assertEquals(2, reversal.getLines().size());
        // Line 1: AP should now be Credit 2500
        assertEquals(Money.zero(usd), reversal.getLines().get(0).debit());
        assertEquals(Money.of(2500L, usd), reversal.getLines().get(0).credit());

        // Line 2: Bank should now be Debit 2500
        assertEquals(Money.of(2500L, usd), reversal.getLines().get(1).debit());
        assertEquals(Money.zero(usd), reversal.getLines().get(1).credit());

        assertDoesNotThrow(() -> reversal.post());
    }

    @Test
    @DisplayName("Fixed Asset: Straight-line monthly depreciation calculation")
    void testFixedAssetStraightLineDepreciation() {
        FixedAsset asset = new FixedAsset(
                FixedAssetId.generate(),
                "FA-001",
                "MacBook Pro M3 Max",
                AccountId.generate(),
                AccountId.generate(),
                AccountId.generate(),
                Money.of(new BigDecimal("3600.00"), usd),
                Money.of(new BigDecimal("0.00"), usd),
                36,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                LocalDate.now()
        );

        Money monthly = asset.calculateMonthlyDepreciation();
        assertEquals(new BigDecimal("100.00"), monthly.amount());

        asset.applyMonthlyDepreciation(monthly);
        assertEquals(new BigDecimal("3500.00"), asset.getCurrentBookValue().amount());
        assertEquals(1, asset.getDepreciatedMonths());
    }

    @Test
    @DisplayName("Fiscal Period: Lifecycle and date containment")
    void testFiscalPeriodLifecycle() {
        FiscalPeriod period = new FiscalPeriod(
                FiscalPeriodId.generate(),
                1,
                "2026-01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31)
        );

        assertTrue(period.isOpen());
        assertTrue(period.contains(LocalDate.of(2026, 1, 15)));
        assertFalse(period.contains(LocalDate.of(2026, 2, 1)));

        period.close();
        assertFalse(period.isOpen());
        assertEquals(FiscalPeriod.PeriodStatus.CLOSED, period.getStatus());

        period.reopen();
        assertTrue(period.isOpen());

        period.lock();
        assertEquals(FiscalPeriod.PeriodStatus.LOCKED, period.getStatus());
        assertThrows(IllegalStateException.class, period::reopen);
    }
}
