package tech.kayys.numou.project.domain.task;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record TaskDependencyId(UUID value)
        implements DomainId<UUID> {

    public TaskDependencyId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Task dependency id cannot be null"
            );
        }
    }

    public static TaskDependencyId newId() {
        return new TaskDependencyId(UUID.randomUUID());
    }
}
