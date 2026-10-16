package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.application.cqrs.Query;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.report.IncomeStatement;
import tech.kayys.syirkah.accounting.domain.report.ReportPeriod;

import java.util.Objects;

public record GetIncomeStatementQuery(
        TenantRef tenantId,
        LedgerId ledgerId,
        ReportPeriod period
) implements Query<IncomeStatement> {
    public GetIncomeStatementQuery {
        Objects.requireNonNull(period, "period cannot be null");
    }
}
