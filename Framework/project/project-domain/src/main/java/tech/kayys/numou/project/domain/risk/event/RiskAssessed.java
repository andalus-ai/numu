package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskScore;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RiskAssessed(
        UUID eventId,
        Instant occurredAt,
        RiskId riskId,
        ProjectId projectId,
        RiskScore score
) implements DomainEvent {

    public RiskAssessed {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(score, "score cannot be null");
    }

    @Override
    public String eventType() {
        return "project.risk-assessed";
    }
}
