package tech.kayys.numou.accounting.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;

import java.time.Instant;

public record InvoiceStatisticsQuery(
        Instant fromDate,
        Instant toDate
) implements Query {}
