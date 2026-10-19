package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.application.cqrs.Query;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.report.IncomeStatement;
import tech.kayys.numou.accounting.domain.report.ReportPeriod;

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
