package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.workforce.domain.worker.WorkerType;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers a new worker in the workforce system.
 */
public record RegisterWorkerCommand(
        UUID personId,
        WorkerType workerType,
        String actor
) implements Command {

    public RegisterWorkerCommand {
        Objects.requireNonNull(personId, "personId cannot be null");
        Objects.requireNonNull(workerType, "workerType cannot be null");
    }
}
