package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.util.Objects;

public record ActivateWorkerCommand(
        WorkerId workerId,
        String actor
) implements Command {

    public ActivateWorkerCommand {
        Objects.requireNonNull(workerId, "workerId cannot be null");
    }
}
