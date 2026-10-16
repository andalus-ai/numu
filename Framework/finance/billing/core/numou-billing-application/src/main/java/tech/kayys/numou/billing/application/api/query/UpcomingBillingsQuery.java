package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.foundation.application.query.Query;

public record UpcomingBillingsQuery(
        int daysAhead
) implements Query {}
