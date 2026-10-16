package tech.kayys.numou.workforce.domain.benefit.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.benefit.BenefitId;
import tech.kayys.numou.workforce.domain.benefit.WorkerBenefitEnrollmentId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerBenefitEnrolled(
        UUID eventId,
        Instant occurredAt,
        WorkerBenefitEnrollmentId enrollmentId,
        WorkerId workerId,
        EmploymentId employmentId,
        BenefitId benefitId,
        LocalDate effectiveFrom
) implements DomainEvent {
    public WorkerBenefitEnrolled(
            WorkerBenefitEnrollmentId enrollmentId,
            WorkerId workerId,
            EmploymentId employmentId,
            BenefitId benefitId,
            LocalDate effectiveFrom
    ) {
        this(UUID.randomUUID(), Instant.now(), enrollmentId, workerId, employmentId, benefitId, effectiveFrom);
    }

    @Override
    public String eventType() { return "workforce.benefit.worker-enrolled"; }
}
