package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.worker.WorkerType;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers a new worker in the workforce system. The tenant must be resolved by the trusted application boundary,
 * never accepted directly from an untrusted request payload.
 */
public record RegisterWorkerCommand(
        TenantId tenantId,
        UUID personId,
        WorkerType workerType,
        String actor
) implements Command {

    public RegisterWorkerCommand {
        Objects.requireNonNull(tenantId, "tenantId must be resolved from trusted authenticated context");
        Objects.requireNonNull(personId, "personId cannot be null");
        Objects.requireNonNull(workerType, "workerType cannot be null");
    }
}
