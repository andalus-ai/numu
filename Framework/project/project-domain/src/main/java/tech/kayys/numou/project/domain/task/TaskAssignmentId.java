package tech.kayys.syirkah.project.domain.task;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

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
