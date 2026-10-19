package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.accounting.domain.valueobject.InvoiceStatus;
import tech.kayys.numou.foundation.application.query.Query;

import java.time.Instant;
import java.util.UUID;

public record SearchInvoicesQuery(
        UUID customerId,
        InvoiceStatus status,
        Instant fromDate,
        Instant toDate,
        Double minAmount,
        Double maxAmount,
        int page,
        int size,
        SortBy sortBy
) implements Query {

    public enum SortBy {
        INVOICE_DATE_DESC,
        INVOICE_DATE_ASC,
        DUE_DATE_DESC,
        DUE_DATE_ASC,
        AMOUNT_DESC,
        AMOUNT_ASC
    }
}
