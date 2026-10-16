package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.billing.domain.identifier.BillingScheduleId;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import java.time.Instant;

public record BillingCycleResult(
        BillingScheduleId scheduleId,
        int cycleNumber,
        boolean success,
        Money amount,
        String invoiceId,
        String transactionId,
        String message,
        Instant processedAt
) {}
