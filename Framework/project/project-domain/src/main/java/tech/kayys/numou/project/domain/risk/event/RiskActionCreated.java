package tech.kayys.syirkah.project.domain.risk.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskTreatmentActionId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a treatment action is added to a risk. Carries the
 * projection-facing fields (title, due date) so overdue-action read
 * models can be built from the event stream alone.
 */
public record RiskActionCreated(
        UUID eventId,
        Instant occurredAt,
        RiskTreatmentActionId actionId,
        RiskId riskId,
        ProjectId projectId,
        String title,
        LocalDate dueDate
) implements DomainEvent {

    public RiskActionCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(actionId, "actionId cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(dueDate, "dueDate cannot be null");
    }

    @Override
    public String eventType() {
        return "project.risk-action-created";
    }
}