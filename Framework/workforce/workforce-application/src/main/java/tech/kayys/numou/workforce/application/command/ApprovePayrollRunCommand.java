package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;

import java.util.Objects;

public record ApprovePayrollRunCommand(
        PayrollRunId runId
) implements Command {
    public ApprovePayrollRunCommand {
        Objects.requireNonNull(runId, "runId must not be null");
    }
}
