package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

/**
 * Command to approve a submitted timesheet.
 */
public record ApproveTimesheetCommand(
        TimesheetId timesheetId,
        WorkerId approverId,
        String approverActor,
        String notes
) implements Command {
    public ApproveTimesheetCommand {
        Objects.requireNonNull(timesheetId, "timesheetId cannot be null");
        Objects.requireNonNull(approverId, "approverId cannot be null");
        Objects.requireNonNull(approverActor, "approverActor cannot be null");
    }
}
