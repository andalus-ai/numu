package tech.kayys.numou.workforce.domain.compliance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.compliance.ComplianceAssessmentId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceAssessmentStatus;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record ComplianceAssessmentRecorded(
        UUID eventId,
        Instant occurredAt,
        ComplianceAssessmentId id,
        WorkerId workerId,
        ComplianceAssessmentStatus status
) implements DomainEvent {
    public ComplianceAssessmentRecorded(ComplianceAssessmentId id, WorkerId workerId, ComplianceAssessmentStatus status) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, status);
    }

    @Override
    public String eventType() {
        return "workforce.compliance.assessment.recorded";
    }
}
