package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;
import java.time.Instant;

public record BillingStatisticsQuery(
        Instant fromDate,
        Instant toDate
) implements Query {}
