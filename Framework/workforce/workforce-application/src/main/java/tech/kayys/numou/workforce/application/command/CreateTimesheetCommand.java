package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

public record CreateTimesheetCommand(
        WorkerId workerId,
        EmploymentId employmentId,
        LocalDate periodStart,
        LocalDate periodEnd
) implements Command {
    public CreateTimesheetCommand {
        Objects.requireNonNull(workerId, "workerId cannot be null");
        Objects.requireNonNull(employmentId, "employmentId cannot be null");
        Objects.requireNonNull(periodStart, "periodStart cannot be null");
        Objects.requireNonNull(periodEnd, "periodEnd cannot be null");
    }
}
