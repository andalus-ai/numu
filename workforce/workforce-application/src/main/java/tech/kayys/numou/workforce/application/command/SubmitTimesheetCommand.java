package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Command to submit a timesheet for approval.
 */
public record SubmitTimesheetCommand(
        TimesheetId timesheetId,
        WorkerId workerId,
        EmploymentId employmentId,
        LocalDate submittedAt,
        String actor
) implements Command {
    public SubmitTimesheetCommand {
        Objects.requireNonNull(timesheetId, "timesheetId cannot be null");
        Objects.requireNonNull(workerId, "workerId cannot be null");
        Objects.requireNonNull(employmentId, "employmentId cannot be null");
        Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
        Objects.requireNonNull(actor, "actor cannot be null");
    }
}
