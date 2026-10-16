package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record BalanceSheet(
        LocalDate asOfDate,
        List<ReportLine> assets,
        Money totalAssets,
        List<ReportLine> liabilities,
        Money totalLiabilities,
        List<ReportLine> equity,
        Money totalEquity,
        boolean balanced
) implements ValueObject {

    public static BalanceSheet of(
            LocalDate asOfDate,
            List<ReportLine> assets,
            Money totalAssets,
            List<ReportLine> liabilities,
            Money totalLiabilities,
            List<ReportLine> equity,
            Money totalEquity
    ) {
        Money liabilitiesAndEquity = totalLiabilities.add(totalEquity);
        boolean isBalanced = Objects.equals(totalAssets, liabilitiesAndEquity);
        return new BalanceSheet(asOfDate, List.copyOf(assets), totalAssets, List.copyOf(liabilities), totalLiabilities, List.copyOf(equity), totalEquity, isBalanced);
    }
}
