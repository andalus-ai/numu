package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Adds one treatment action to a risk. Actions live outside the risk
 * aggregate and are persisted separately.
 */
public record CreateRiskTreatmentActionCommand(
        ProjectId projectId,
        RiskId riskId,
        String title,
        String description,
        UUID ownerId,
        LocalDate dueDate,
        Money estimatedCost
) implements Command {

    public CreateRiskTreatmentActionCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(riskId, "riskId cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(dueDate, "dueDate cannot be null");
        Objects.requireNonNull(estimatedCost, "estimatedCost cannot be null");
    }
}
