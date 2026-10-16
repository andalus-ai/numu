package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.domain.identifier.CustomerId;
import tech.kayys.numou.foundation.application.query.Query;

import java.util.UUID;

public record GetInvoiceSummaryQuery(CustomerId customerId) implements Query {
    public GetInvoiceSummaryQuery(UUID customerId) {
        this(CustomerId.of(customerId));
    }
}
