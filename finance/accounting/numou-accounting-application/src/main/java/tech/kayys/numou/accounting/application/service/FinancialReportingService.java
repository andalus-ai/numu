package tech.kayys.numou.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.report.*;
import tech.kayys.numou.accounting.domain.valueobject.AccountType;
import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FinancialReportingService {

    private final AccountRepository accountRepository;

    public FinancialReportingService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    public Uni<TrialBalance> generateTrialBalance(TenantRef tenantId, LedgerId ledgerId, LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
        return scopedAccounts(tenantId, ledgerId).map(accounts -> {
            Currency currency = reportCurrency(accounts);
            Money totalDebit = Money.zero(currency);
            Money totalCredit = Money.zero(currency);
            List<TrialBalance.TrialBalanceLine> lines = new ArrayList<>();

            for (Account account : accounts) {
                Money balance = account.getCurrentBalance();
                Money debit = Money.zero(balance.currency());
                Money credit = Money.zero(balance.currency());
                if (account.getAccountType().isNormalDebitBalance()) {
                    debit = balance;
                    totalDebit = totalDebit.add(debit);
                } else {
                    credit = balance;
                    totalCredit = totalCredit.add(credit);
                }
                lines.add(new TrialBalance.TrialBalanceLine(account.id(), account.getAccountNumber(),
                        account.getName(), account.getAccountType(), debit, credit));
            }
            return TrialBalance.of(asOfDate, lines, totalDebit, totalCredit);
        });
    }

    public Uni<BalanceSheet> generateBalanceSheet(TenantRef tenantId, LedgerId ledgerId, LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
        return scopedAccounts(tenantId, ledgerId).map(accounts -> {
            Currency currency = reportCurrency(accounts);
            List<ReportLine> assets = new ArrayList<>();
            List<ReportLine> liabilities = new ArrayList<>();
            List<ReportLine> equity = new ArrayList<>();
            Money totalAssets = Money.zero(currency);
            Money totalLiabilities = Money.zero(currency);
            Money totalEquity = Money.zero(currency);

            for (Account account : accounts) {
                ReportLine line = new ReportLine(account.id(), account.getAccountNumber(),
                        account.getName(), account.getCurrentBalance());
                if (account.getAccountType() == AccountType.ASSET) {
                    assets.add(line);
                    totalAssets = totalAssets.add(account.getCurrentBalance());
                } else if (account.getAccountType() == AccountType.LIABILITY) {
                    liabilities.add(line);
                    totalLiabilities = totalLiabilities.add(account.getCurrentBalance());
                } else if (account.getAccountType() == AccountType.EQUITY) {
                    equity.add(line);
                    totalEquity = totalEquity.add(account.getCurrentBalance());
                }
            }
            return BalanceSheet.of(asOfDate, assets, totalAssets, liabilities, totalLiabilities, equity, totalEquity);
        });
    }

    public Uni<IncomeStatement> generateIncomeStatement(
            TenantRef tenantId, LedgerId ledgerId, ReportPeriod period) {
        Objects.requireNonNull(period, "period cannot be null");
        return scopedAccounts(tenantId, ledgerId).map(accounts -> {
            Currency currency = reportCurrency(accounts);
            List<ReportLine> revenues = new ArrayList<>();
            List<ReportLine> expenses = new ArrayList<>();
            Money totalRevenue = Money.zero(currency);
            Money totalExpense = Money.zero(currency);

            for (Account account : accounts) {
                ReportLine line = new ReportLine(account.id(), account.getAccountNumber(),
                        account.getName(), account.getCurrentBalance());
                if (account.getAccountType() == AccountType.REVENUE) {
                    revenues.add(line);
                    totalRevenue = totalRevenue.add(account.getCurrentBalance());
                } else if (account.getAccountType() == AccountType.EXPENSE) {
                    expenses.add(line);
                    totalExpense = totalExpense.add(account.getCurrentBalance());
                }
            }
            return IncomeStatement.of(period, revenues, totalRevenue, expenses, totalExpense);
        });
    }

    /**
     * Compatibility entry point for legacy single-tenant callers. New application
     * code must pass tenant and ledger scope explicitly.
     */
    @Deprecated(forRemoval = false)
    public Uni<TrialBalance> generateTrialBalance(LocalDate asOfDate) {
        return generateTrialBalance(TenantRef.defaultTenant(), LedgerId.primary(), asOfDate);
    }

    @Deprecated(forRemoval = false)
    public Uni<BalanceSheet> generateBalanceSheet(LocalDate asOfDate) {
        return generateBalanceSheet(TenantRef.defaultTenant(), LedgerId.primary(), asOfDate);
    }

    @Deprecated(forRemoval = false)
    public Uni<IncomeStatement> generateIncomeStatement(ReportPeriod period) {
        return generateIncomeStatement(TenantRef.defaultTenant(), LedgerId.primary(), period);
    }

    public Uni<CashFlowStatement> generateCashFlowStatement(ReportPeriod period) {
        Objects.requireNonNull(period, "period cannot be null");
        Currency currency = Currency.of("USD");
        return Uni.createFrom().item(CashFlowStatement.of(
                period, Money.zero(currency), Money.zero(currency), Money.zero(currency)));
    }

    public Uni<ShariaFundsStatement> generateShariaFundsStatement(ReportPeriod period) {
        Objects.requireNonNull(period, "period cannot be null");
        Currency currency = Currency.of("IDR");
        return Uni.createFrom().item(ShariaFundsStatement.of(
                period, Money.zero(currency), Money.zero(currency),
                Money.zero(currency), Money.zero(currency),
                Money.zero(currency), Money.zero(currency)));
    }

    private Uni<List<Account>> scopedAccounts(TenantRef tenantId, LedgerId ledgerId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        return accountRepository.findAll(tenantId, ledgerId);
    }

    private static Currency reportCurrency(List<Account> accounts) {
        if (accounts.isEmpty()) return Currency.of("USD");
        Currency currency = accounts.get(0).getCurrentBalance().currency();
        boolean mixedCurrency = accounts.stream()
                .anyMatch(account -> !currency.equals(account.getCurrentBalance().currency()));
        if (mixedCurrency) {
            throw new IllegalStateException(
                    "A single-currency financial report cannot aggregate accounts with different currencies");
        }
        return currency;
    }
}
