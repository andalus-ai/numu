package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.application.cqrs.Query;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.report.CashFlowStatement;
import tech.kayys.numou.accounting.domain.report.ReportPeriod;

import java.util.Objects;

public record GetCashFlowQuery(
        TenantRef tenantId,
        LedgerId ledgerId,
        ReportPeriod period
) implements Query<CashFlowStatement> {
    public GetCashFlowQuery {
        Objects.requireNonNull(period, "period cannot be null");
    }
}
