package tech.kayys.syirkah.project.application.risk.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.RiskCategory;
import tech.kayys.syirkah.project.domain.risk.RiskSource;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Identifies a new risk. The number is caller-supplied in v1; a
 * per-tenant generator port can replace it later without touching the
 * aggregate.
 */
public record IdentifyRiskCommand(
        ProjectId projectId,
        String number,
        String title,
        String description,
        RiskCategory category,
        RiskSource source,
        LocalDate identifiedDate,
        LocalDate targetDate
) implements Command {

    public IdentifyRiskCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(category, "category cannot be null");
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(identifiedDate, "identifiedDate cannot be null");
        Objects.requireNonNull(targetDate, "targetDate cannot be null");
    }
}
