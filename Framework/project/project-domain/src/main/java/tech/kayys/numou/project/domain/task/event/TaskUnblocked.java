package tech.kayys.syirkah.project.domain.task.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskUnblocked(
        UUID eventId,
        Instant occurredAt,
        ProjectTaskId taskId,
        ProjectId projectId
) implements DomainEvent {

    public TaskUnblocked {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
    }

    public static TaskUnblocked now(
            ProjectTaskId taskId,
            ProjectId projectId
    ) {
        return new TaskUnblocked(
                UUID.randomUUID(),
                Instant.now(),
                taskId,
                projectId
        );
    }

    @Override
    public String eventType() {
        return "project.task-unblocked";
    }
}
