package tech.kayys.syirkah.accounting.application.api.query;

import tech.kayys.syirkah.foundation.application.query.Query;

import java.time.Instant;

public record InvoiceStatisticsQuery(
        Instant fromDate,
        Instant toDate
) implements Query {}
