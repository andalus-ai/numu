package tech.kayys.syirkah.project.application.command;

import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/** Fixes the planned period of a DRAFT project and moves it to PLANNED. */
public record PlanProjectCommand(
        ProjectId projectId,
        DateRange plannedPeriod
) implements Command {

    public PlanProjectCommand {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(plannedPeriod, "plannedPeriod cannot be null");
    }
}
