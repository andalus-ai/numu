package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.foundation.domain.valueobject.Money;

public record BatchBillingResult(
        int totalProcessed,
        int successful,
        int failed,
        int resultsCount,
        Money totalAmount,
        String message
) {}
