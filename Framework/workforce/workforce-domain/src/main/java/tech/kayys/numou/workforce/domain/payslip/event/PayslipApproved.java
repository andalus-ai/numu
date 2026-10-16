package tech.kayys.numou.workforce.domain.payslip.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.payslip.PayslipId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PayslipApproved(
        UUID eventId,
        Instant occurredAt,
        PayslipId payslipId,
        WorkerId workerId
) implements DomainEvent {
    public PayslipApproved(PayslipId payslipId, WorkerId workerId) {
        this(UUID.randomUUID(), Instant.now(), payslipId, workerId);
    }

    @Override
    public String eventType() { return "workforce.payroll.payslip-approved"; }
}
