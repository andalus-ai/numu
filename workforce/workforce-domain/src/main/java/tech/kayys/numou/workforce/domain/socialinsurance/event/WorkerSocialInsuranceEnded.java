package tech.kayys.numou.workforce.domain.socialinsurance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.socialinsurance.SocialInsuranceSchemeId;
import tech.kayys.numou.workforce.domain.socialinsurance.WorkerSocialInsuranceEnrollmentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerSocialInsuranceEnded(
        UUID eventId,
        Instant occurredAt,
        WorkerSocialInsuranceEnrollmentId enrollmentId,
        WorkerId workerId,
        SocialInsuranceSchemeId schemeId,
        LocalDate effectiveTo
) implements DomainEvent {
    public WorkerSocialInsuranceEnded(
            WorkerSocialInsuranceEnrollmentId enrollmentId,
            WorkerId workerId,
            SocialInsuranceSchemeId schemeId,
            LocalDate effectiveTo
    ) {
        this(UUID.randomUUID(), Instant.now(), enrollmentId, workerId, schemeId, effectiveTo);
    }

    @Override
    public String eventType() { return "workforce.socialinsurance.worker-ended"; }
}
