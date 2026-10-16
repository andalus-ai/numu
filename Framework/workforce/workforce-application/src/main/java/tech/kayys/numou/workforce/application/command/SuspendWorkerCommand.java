package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

public record SuspendWorkerCommand(
        WorkerId workerId,
        String actor,
        String reason
) implements Command {

    public SuspendWorkerCommand {
        Objects.requireNonNull(workerId, "workerId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
