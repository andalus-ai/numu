package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record TrialBalance(
        LocalDate asOfDate,
        List<TrialBalanceLine> lines,
        Money totalDebit,
        Money totalCredit,
        boolean balanced
) implements ValueObject {

    public record TrialBalanceLine(
            AccountId accountId,
            String accountNumber,
            String accountName,
            AccountType accountType,
            Money debitBalance,
            Money creditBalance
    ) implements ValueObject {}

    public static TrialBalance of(LocalDate asOfDate, List<TrialBalanceLine> lines, Money totalDebit, Money totalCredit) {
        boolean isBalanced = Objects.equals(totalDebit, totalCredit);
        return new TrialBalance(asOfDate, List.copyOf(lines), totalDebit, totalCredit, isBalanced);
    }
}
