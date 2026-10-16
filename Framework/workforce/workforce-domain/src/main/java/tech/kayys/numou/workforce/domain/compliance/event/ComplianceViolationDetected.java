package tech.kayys.numou.workforce.domain.compliance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.compliance.ComplianceAssessmentId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record ComplianceViolationDetected(
        UUID eventId,
        Instant occurredAt,
        ComplianceAssessmentId id,
        WorkerId workerId,
        ComplianceRequirementId requirementId,
        String notes
) implements DomainEvent {
    public ComplianceViolationDetected(ComplianceAssessmentId id, WorkerId workerId, ComplianceRequirementId requirementId, String notes) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, requirementId, notes);
    }

    @Override
    public String eventType() {
        return "workforce.compliance.violation.detected";
    }
}
