package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.application.cqrs.Query;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.report.BalanceSheet;

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
