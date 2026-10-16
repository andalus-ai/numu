package tech.kayys.numou.workforce.domain.compliance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.domain.compliance.PolicyAcknowledgementId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PolicyAcknowledged(
        UUID eventId,
        Instant occurredAt,
        PolicyAcknowledgementId id,
        WorkerId workerId,
        ComplianceRequirementId requirementId,
        Instant acknowledgedAt
) implements DomainEvent {
    public PolicyAcknowledged(PolicyAcknowledgementId id, WorkerId workerId, ComplianceRequirementId requirementId, Instant acknowledgedAt) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, requirementId, acknowledgedAt);
    }

    @Override
    public String eventType() {
        return "workforce.compliance.policy.acknowledged";
    }
}
