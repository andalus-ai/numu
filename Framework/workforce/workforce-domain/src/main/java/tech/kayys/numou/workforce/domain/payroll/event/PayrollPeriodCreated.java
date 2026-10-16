package tech.kayys.numou.workforce.domain.payroll.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollPeriodCreated(
        UUID eventId,
        Instant occurredAt,
        PayrollPeriodId periodId,
        TenantId tenantId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate paymentDate
) implements DomainEvent {
    public PayrollPeriodCreated(
            PayrollPeriodId id,
            TenantId tenantId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate paymentDate
    ) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, startDate, endDate, paymentDate);
    }

    @Override
    public String eventType() { return "workforce.payroll.period-created"; }
}
