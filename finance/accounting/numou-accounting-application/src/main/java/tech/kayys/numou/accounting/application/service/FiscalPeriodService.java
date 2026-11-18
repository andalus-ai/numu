package tech.kayys.numou.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.numou.accounting.application.port.JournalEntryRepository;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.model.FiscalPeriod;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FiscalPeriodService {
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public FiscalPeriodService(FiscalPeriodRepository fiscalPeriodRepository, AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository) {
        this.fiscalPeriodRepository = Objects.requireNonNull(fiscalPeriodRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
    }

    public Uni<Void> closePeriod(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId periodId) {
        return findScoped(tenantId, ledgerId, periodId).chain(period -> {
            period.close();
            return fiscalPeriodRepository.save(period);
        });
    }

    public Uni<Void> lockPeriod(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId periodId) {
        return findScoped(tenantId, ledgerId, periodId).chain(period -> {
            period.lock();
            return fiscalPeriodRepository.save(period);
        });
    }

    public Uni<Void> reopenPeriod(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId periodId) {
        return findScoped(tenantId, ledgerId, periodId).chain(period -> {
            period.reopen();
            return fiscalPeriodRepository.save(period);
        });
    }

    private Uni<FiscalPeriod> findScoped(TenantRef tenantId, LedgerId ledgerId, FiscalPeriodId periodId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerId, "ledgerId");
        Objects.requireNonNull(periodId, "periodId");
        return fiscalPeriodRepository.findById(tenantId, ledgerId, periodId)
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Period not found"));
                    }
                    return Uni.createFrom().item(optional.get());
                });
    }

    /** @deprecated Supply tenant and ledger scope explicitly. */
    @Deprecated(forRemoval = true)
    public Uni<Void> closePeriod(FiscalPeriodId periodId) {
        return closePeriod(TenantRef.defaultTenant(), LedgerId.primary(), periodId);
    }

    /** @deprecated Supply tenant and ledger scope explicitly. */
    @Deprecated(forRemoval = true)
    public Uni<Void> lockPeriod(FiscalPeriodId periodId) {
        return lockPeriod(TenantRef.defaultTenant(), LedgerId.primary(), periodId);
    }

    /** @deprecated Supply tenant and ledger scope explicitly. */
    @Deprecated(forRemoval = true)
    public Uni<Void> reopenPeriod(FiscalPeriodId periodId) {
        return reopenPeriod(TenantRef.defaultTenant(), LedgerId.primary(), periodId);
    }

    /**
     * Legacy year-end sweep. This method remains unscoped and must not be exposed
     * from a tenant-aware production endpoint until its command carries scope.
     */
    @Deprecated(forRemoval = true)
    public Uni<JournalEntryId> performYearEndClosing(int year, AccountId retainedEarningsAccountId, String closedBy) {
        return accountRepository.findAll()
                .chain(accounts -> {
                    JournalEntryId jeId = JournalEntryId.generate();
                    JournalEntry closingEntry = new JournalEntry(jeId,
                            "YEC-" + year + "-" + UUID.randomUUID().toString().substring(0, 4),
                            Instant.now(), "Year-End Closing Sweep for Fiscal Year " + year + " (Closed by: " + closedBy + ")");
                    Currency defaultCurrency = Currency.of("USD");
                    Money totalNetRevenueExpense = Money.zero(defaultCurrency);
                    for (Account acc : accounts) {
                        if (acc.getAccountType() == AccountType.REVENUE && !acc.getCurrentBalance().isZero()) {
                            Money balance = acc.getCurrentBalance();
                            closingEntry.addLine(acc.id(), balance, Money.zero(balance.currency()), "Year-end revenue sweep: " + acc.getName());
                            totalNetRevenueExpense = totalNetRevenueExpense.add(balance);
                        } else if (acc.getAccountType() == AccountType.EXPENSE && !acc.getCurrentBalance().isZero()) {
                            Money balance = acc.getCurrentBalance();
                            closingEntry.addLine(acc.id(), Money.zero(balance.currency()), balance, "Year-end expense sweep: " + acc.getName());
                            totalNetRevenueExpense = totalNetRevenueExpense.subtract(balance);
                        }
                    }
                    if (totalNetRevenueExpense.isPositive()) {
                        closingEntry.addLine(retainedEarningsAccountId, Money.zero(totalNetRevenueExpense.currency()), totalNetRevenueExpense, "Retained Earnings - Net Profit");
                    } else if (totalNetRevenueExpense.isNegative()) {
                        Money loss = totalNetRevenueExpense.abs();
                        closingEntry.addLine(retainedEarningsAccountId, loss, Money.zero(loss.currency()), "Retained Earnings - Net Loss");
                    }
                    if (closingEntry.getLines().isEmpty()) return Uni.createFrom().item(jeId);
                    closingEntry.post();
                    return journalEntryRepository.save(closingEntry).map(ignored -> jeId);
                });
    }
}
