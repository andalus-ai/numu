package tech.kayys.numou.billing.application.api.query;

import java.util.List;

public record UpcomingBillingsView(
        List<BillingScheduleView> schedules,
        int daysAhead
) {}
