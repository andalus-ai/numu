package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.identifier.CustomerId;
import tech.kayys.syirkah.foundation.application.query.Query;

import java.util.UUID;

public record GetInvoiceSummaryQuery(CustomerId customerId) implements Query {
    public GetInvoiceSummaryQuery(UUID customerId) {
        this(CustomerId.of(customerId));
    }
}
