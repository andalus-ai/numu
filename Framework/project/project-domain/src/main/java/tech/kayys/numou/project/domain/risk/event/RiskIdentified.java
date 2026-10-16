package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskCategory;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskSource;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RiskIdentified(
        UUID eventId,
        Instant occurredAt,
        RiskId riskId,
        ProjectId projectId,
        String number,
        RiskCategory category,
        RiskSource source
) implements DomainEvent {

    public RiskIdentified {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
    }

    @Override
    public String eventType() {
        return "project.risk-identified";
    }
}
