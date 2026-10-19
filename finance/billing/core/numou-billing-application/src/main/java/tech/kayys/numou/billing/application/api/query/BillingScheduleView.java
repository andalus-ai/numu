package tech.kayys.numou.billing.application.api.query;

import tech.kayys.numou.billing.domain.model.BillingSchedule;
import tech.kayys.numou.billing.domain.valueobject.BillingFrequency;
import tech.kayys.numou.billing.domain.valueobject.BillingStatus;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.UUID;

public record BillingScheduleView(
        String id,
        UUID subscriptionId,
        String customerId,
        String customerEmail,
        BillingFrequency frequency,
        BillingStatus status,
        Instant startDate,
        Instant endDate,
        Instant nextBillingDate,
        Instant lastBillingDate,
        Money amount,
        int currentCycle,
        Integer totalCycles,
        int failedPaymentCount,
        boolean active
) {
    public static BillingScheduleView fromDomain(BillingSchedule schedule) {
        return new BillingScheduleView(
                schedule.getId().toString(),
                schedule.getSubscriptionId(),
                schedule.getCustomerId(),
                schedule.getCustomerEmail(),
                schedule.getFrequency(),
                schedule.getStatus(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getNextBillingDate(),
                schedule.getLastBillingDate(),
                schedule.getAmount(),
                schedule.getCurrentCycle(),
                schedule.getTotalCycles(),
                schedule.getFailedPaymentCount(),
                schedule.isActive()
        );
    }
}
