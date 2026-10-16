package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.accounting.domain.valueobject.InvoiceStatus;
import tech.kayys.syirkah.foundation.application.query.Query;

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
