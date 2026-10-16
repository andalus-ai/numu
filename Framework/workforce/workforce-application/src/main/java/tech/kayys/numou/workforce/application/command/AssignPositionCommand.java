package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.Objects;

public record AssignPositionCommand(
        PositionId positionId,
        WorkerId workerId,
        EmploymentId employmentId,
        LocalDate startDate
) implements Command {

    public AssignPositionCommand {
        Objects.requireNonNull(positionId, "positionId cannot be null");
        Objects.requireNonNull(workerId, "workerId cannot be null");
        Objects.requireNonNull(employmentId, "employmentId cannot be null");
        Objects.requireNonNull(startDate, "startDate cannot be null");
    }
}
