package tech.kayys.syirkah.accounting.domain.report;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

public record CashFlowStatement(
        ReportPeriod period,
        Money operatingActivities,
        Money investingActivities,
        Money financingActivities,
        Money netCashFlow
) implements ValueObject {

    public static CashFlowStatement of(
            ReportPeriod period,
            Money operating,
            Money investing,
            Money financing
    ) {
        Money net = operating.add(investing).add(financing);
        return new CashFlowStatement(period, operating, investing, financing, net);
    }
}
