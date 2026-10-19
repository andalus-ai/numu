package tech.kayys.numou.project.domain.task;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record TaskAssignmentId(UUID value)
        implements DomainId<UUID> {

    public TaskAssignmentId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Task assignment id cannot be null"
            );
        }
    }

    public static TaskAssignmentId newId() {
        return new TaskAssignmentId(UUID.randomUUID());
    }
}
