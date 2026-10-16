package tech.kayys.syirkah.project.domain.task;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record ProjectTaskId(UUID value)
        implements DomainId<UUID> {

    public ProjectTaskId {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Project task id cannot be null"
            );
        }
    }

    public static ProjectTaskId newId() {
        return new ProjectTaskId(UUID.randomUUID());
    }
}
