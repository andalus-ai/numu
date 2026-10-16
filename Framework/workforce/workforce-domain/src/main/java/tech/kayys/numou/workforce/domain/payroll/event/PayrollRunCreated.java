package tech.kayys.numou.workforce.domain.payroll.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;

import java.time.Instant;
import java.util.UUID;

public record PayrollRunCreated(
        UUID eventId,
        Instant occurredAt,
        PayrollRunId runId,
        PayrollPeriodId periodId,
        TenantId tenantId
) implements DomainEvent {
    public PayrollRunCreated(PayrollRunId runId, PayrollPeriodId periodId, TenantId tenantId) {
        this(UUID.randomUUID(), Instant.now(), runId, periodId, tenantId);
    }

    @Override
    public String eventType() { return "workforce.payroll.run-created"; }
}
