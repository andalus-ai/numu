package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.report.*;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FinancialReportingService {

    private final AccountRepository accountRepository;

    public FinancialReportingService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    public Uni<TrialBalance> generateTrialBalance(LocalDate asOfDate) {
        return accountRepository.findAll()
                .map(accounts -> {
                    List<TrialBalance.TrialBalanceLine> lines = new ArrayList<>();
                    Currency cur = Currency.of("USD");
                    Money totalDebit = Money.zero(cur);
                    Money totalCredit = Money.zero(cur);

                    for (Account acc : accounts) {
                        Money bal = acc.getCurrentBalance();
                        Money deb = Money.zero(bal.currency());
                        Money cred = Money.zero(bal.currency());

                        if (acc.getAccountType().isNormalDebitBalance()) {
                            deb = bal;
                            totalDebit = totalDebit.add(deb);
                        } else {
                            cred = bal;
                            totalCredit = totalCredit.add(cred);
                        }

                        lines.add(new TrialBalance.TrialBalanceLine(
                                acc.id(),
                                acc.getAccountNumber(),
                                acc.getName(),
                                acc.getAccountType(),
                                deb,
                                cred
                        ));
                    }

                    return TrialBalance.of(asOfDate, lines, totalDebit, totalCredit);
                });
    }

    public Uni<BalanceSheet> generateBalanceSheet(LocalDate asOfDate) {
        return accountRepository.findAll()
                .map(accounts -> {
                    List<ReportLine> assets = new ArrayList<>();
                    List<ReportLine> liabilities = new ArrayList<>();
                    List<ReportLine> equity = new ArrayList<>();

                    Currency cur = Currency.of("USD");
                    Money totalAssets = Money.zero(cur);
                    Money totalLiab = Money.zero(cur);
                    Money totalEq = Money.zero(cur);

                    for (Account acc : accounts) {
                        ReportLine line = new ReportLine(acc.id(), acc.getAccountNumber(), acc.getName(), acc.getCurrentBalance());
                        if (acc.getAccountType() == AccountType.ASSET) {
                            assets.add(line);
                            totalAssets = totalAssets.add(acc.getCurrentBalance());
                        } else if (acc.getAccountType() == AccountType.LIABILITY) {
                            liabilities.add(line);
                            totalLiab = totalLiab.add(acc.getCurrentBalance());
                        } else if (acc.getAccountType() == AccountType.EQUITY) {
                            equity.add(line);
                            totalEq = totalEq.add(acc.getCurrentBalance());
                        }
                    }

                    return BalanceSheet.of(asOfDate, assets, totalAssets, liabilities, totalLiab, equity, totalEq);
                });
    }

    public Uni<IncomeStatement> generateIncomeStatement(ReportPeriod period) {
        return accountRepository.findAll()
                .map(accounts -> {
                    List<ReportLine> revenues = new ArrayList<>();
                    List<ReportLine> expenses = new ArrayList<>();

                    Currency cur = Currency.of("USD");
                    Money totalRev = Money.zero(cur);
                    Money totalExp = Money.zero(cur);

                    for (Account acc : accounts) {
                        ReportLine line = new ReportLine(acc.id(), acc.getAccountNumber(), acc.getName(), acc.getCurrentBalance());
                        if (acc.getAccountType() == AccountType.REVENUE) {
                            revenues.add(line);
                            totalRev = totalRev.add(acc.getCurrentBalance());
                        } else if (acc.getAccountType() == AccountType.EXPENSE) {
                            expenses.add(line);
                            totalExp = totalExp.add(acc.getCurrentBalance());
                        }
                    }

                    return IncomeStatement.of(period, revenues, totalRev, expenses, totalExp);
                });
    }

    public Uni<CashFlowStatement> generateCashFlowStatement(ReportPeriod period) {
        Currency cur = Currency.of("USD");
        return Uni.createFrom().item(CashFlowStatement.of(
                period,
                Money.zero(cur),
                Money.zero(cur),
                Money.zero(cur)
        ));
    }

    public Uni<ShariaFundsStatement> generateShariaFundsStatement(ReportPeriod period) {
        Currency cur = Currency.of("IDR");
        return Uni.createFrom().item(ShariaFundsStatement.of(
                period,
                Money.zero(cur), Money.zero(cur),
                Money.zero(cur), Money.zero(cur),
                Money.zero(cur), Money.zero(cur)
        ));
    }
}
