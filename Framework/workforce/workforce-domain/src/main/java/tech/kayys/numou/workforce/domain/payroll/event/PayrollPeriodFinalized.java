package tech.kayys.numou.workforce.domain.payroll.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;

import java.time.Instant;
import java.util.UUID;

public record PayrollPeriodFinalized(
        UUID eventId,
        Instant occurredAt,
        PayrollPeriodId periodId,
        TenantId tenantId
) implements DomainEvent {
    public PayrollPeriodFinalized(PayrollPeriodId id, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId);
    }

    @Override
    public String eventType() { return "workforce.payroll.period-finalized"; }
}
