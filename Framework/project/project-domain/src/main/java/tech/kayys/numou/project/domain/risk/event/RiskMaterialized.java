package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskResponse;
import tech.kayys.syirkah.project.domain.risk.RiskScore;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Carries the risk's number, title and description as well as its
 * score so the materialization policy can build an Issue without
 * reloading the Risk aggregate — an event should be self-sufficient
 * for its consumer.
 */
public record RiskMaterialized(
        UUID eventId,
        Instant occurredAt,
        RiskId riskId,
        ProjectId projectId,
        String number,
        String title,
        String description,
        RiskScore score,
        RiskResponse response
) implements DomainEvent {

    public RiskMaterialized {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(score, "score cannot be null");
        Objects.requireNonNull(response, "response cannot be null");
    }

    @Override
    public String eventType() {
        return "project.risk-materialized";
    }
}
