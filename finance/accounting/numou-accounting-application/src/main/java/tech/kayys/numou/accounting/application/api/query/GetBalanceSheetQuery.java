package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.application.cqrs.Query;
import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;
import tech.kayys.numou.accounting.domain.report.BalanceSheet;

import java.time.LocalDate;
import java.util.Objects;

public record GetBalanceSheetQuery(
        TenantRef tenantId,
        LedgerId ledgerId,
        LocalDate asOfDate
) implements Query<BalanceSheet> {
    public GetBalanceSheetQuery {
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
    }
}
