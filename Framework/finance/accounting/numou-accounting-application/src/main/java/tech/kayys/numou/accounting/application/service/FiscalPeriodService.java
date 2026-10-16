package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.FiscalPeriodId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.FiscalPeriod;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class FiscalPeriodService {

    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;

    public FiscalPeriodService(
            FiscalPeriodRepository fiscalPeriodRepository,
            AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository
    ) {
        this.fiscalPeriodRepository = Objects.requireNonNull(fiscalPeriodRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
    }

    public Uni<Void> closePeriod(FiscalPeriodId periodId) {
        return fiscalPeriodRepository.findById(periodId)
                .chain(opt -> {
                    if (opt.isEmpty()) return Uni.createFrom().failure(new IllegalArgumentException("Period not found"));
                    FiscalPeriod period = opt.get();
                    period.close();
                    return fiscalPeriodRepository.save(period);
                });
    }

    public Uni<Void> lockPeriod(FiscalPeriodId periodId) {
        return fiscalPeriodRepository.findById(periodId)
                .chain(opt -> {
                    if (opt.isEmpty()) return Uni.createFrom().failure(new IllegalArgumentException("Period not found"));
                    FiscalPeriod period = opt.get();
                    period.lock();
                    return fiscalPeriodRepository.save(period);
                });
    }

    public Uni<Void> reopenPeriod(FiscalPeriodId periodId) {
        return fiscalPeriodRepository.findById(periodId)
                .chain(opt -> {
                    if (opt.isEmpty()) return Uni.createFrom().failure(new IllegalArgumentException("Period not found"));
                    FiscalPeriod period = opt.get();
                    period.reopen();
                    return fiscalPeriodRepository.save(period);
                });
    }

    /**
     * Performs year-end closing sweep: zeroes out Revenue and Expense balances into Retained Earnings.
     */
    public Uni<JournalEntryId> performYearEndClosing(int year, AccountId retainedEarningsAccountId, String closedBy) {
        return accountRepository.findAll()
                .chain(accounts -> {
                    JournalEntryId jeId = JournalEntryId.generate();
                    JournalEntry closingEntry = new JournalEntry(
                            jeId,
                            "YEC-" + year + "-" + UUID.randomUUID().toString().substring(0, 4),
                            Instant.now(),
                            "Year-End Closing Sweep for Fiscal Year " + year + " (Closed by: " + closedBy + ")"
                    );

                    Currency defaultCurrency = Currency.of("USD");
                    Money totalNetRevenueExpense = Money.zero(defaultCurrency);

                    for (Account acc : accounts) {
                        if (acc.getAccountType() == AccountType.REVENUE && !acc.getCurrentBalance().isZero()) {
                            // Debit revenue to zero it out
                            Money bal = acc.getCurrentBalance();
                            closingEntry.addLine(acc.id(), bal, Money.zero(bal.currency()), "Year-end revenue sweep: " + acc.getName());
                            totalNetRevenueExpense = totalNetRevenueExpense.add(bal);
                        } else if (acc.getAccountType() == AccountType.EXPENSE && !acc.getCurrentBalance().isZero()) {
                            // Credit expense to zero it out
                            Money bal = acc.getCurrentBalance();
                            closingEntry.addLine(acc.id(), Money.zero(bal.currency()), bal, "Year-end expense sweep: " + acc.getName());
                            totalNetRevenueExpense = totalNetRevenueExpense.subtract(bal);
                        }
                    }

                    // Balance to retained earnings
                    if (totalNetRevenueExpense.isPositive()) {
                        closingEntry.addLine(retainedEarningsAccountId, Money.zero(totalNetRevenueExpense.currency()), totalNetRevenueExpense, "Retained Earnings - Net Profit");
                    } else if (totalNetRevenueExpense.isNegative()) {
                        Money loss = totalNetRevenueExpense.abs();
                        closingEntry.addLine(retainedEarningsAccountId, loss, Money.zero(loss.currency()), "Retained Earnings - Net Loss");
                    }

                    if (closingEntry.getLines().isEmpty()) {
                        return Uni.createFrom().item(jeId);
                    }

                    closingEntry.post();
                    return journalEntryRepository.save(closingEntry).map(v -> jeId);
                });
    }
}
