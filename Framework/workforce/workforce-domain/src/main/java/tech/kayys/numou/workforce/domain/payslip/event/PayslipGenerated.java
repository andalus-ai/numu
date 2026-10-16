package tech.kayys.numou.workforce.domain.payslip.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.domain.payslip.PayslipId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PayslipGenerated(
        UUID eventId,
        Instant occurredAt,
        PayslipId payslipId,
        PayrollRunId payrollRunId,
        PayrollPeriodId periodId,
        WorkerId workerId,
        EmploymentId employmentId
) implements DomainEvent {
    public PayslipGenerated(
            PayslipId payslipId,
            PayrollRunId payrollRunId,
            PayrollPeriodId periodId,
            WorkerId workerId,
            EmploymentId employmentId
    ) {
        this(UUID.randomUUID(), Instant.now(), payslipId, payrollRunId, periodId, workerId, employmentId);
    }

    @Override
    public String eventType() { return "workforce.payroll.payslip-generated"; }
}
