package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.time.LocalDate;
import java.util.Objects;

public record TerminateEmploymentCommand(
        EmploymentId employmentId,
        LocalDate effectiveDate,
        String reason
) implements Command {

    public TerminateEmploymentCommand {
        Objects.requireNonNull(employmentId, "employmentId cannot be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
