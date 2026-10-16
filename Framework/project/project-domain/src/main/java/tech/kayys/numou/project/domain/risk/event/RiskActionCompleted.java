package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RiskActionCompleted(
        UUID eventId,
        Instant occurredAt,
        RiskTreatmentActionId actionId,
        RiskId riskId,
        ProjectId projectId
) implements DomainEvent {

    public RiskActionCompleted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(actionId, "actionId cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }

    @Override
    public String eventType() {
        return "project.risk-action-completed";
    }
}