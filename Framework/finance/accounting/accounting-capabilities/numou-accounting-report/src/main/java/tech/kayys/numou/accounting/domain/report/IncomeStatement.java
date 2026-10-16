package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.util.List;

public record IncomeStatement(
        ReportPeriod period,
        List<ReportLine> revenues,
        Money totalRevenue,
        List<ReportLine> expenses,
        Money totalExpense,
        Money netIncome
) implements ValueObject {

    public static IncomeStatement of(
            ReportPeriod period,
            List<ReportLine> revenues,
            Money totalRevenue,
            List<ReportLine> expenses,
            Money totalExpense
    ) {
        Money net = totalRevenue.subtract(totalExpense);
        return new IncomeStatement(period, List.copyOf(revenues), totalRevenue, List.copyOf(expenses), totalExpense, net);
    }
}
