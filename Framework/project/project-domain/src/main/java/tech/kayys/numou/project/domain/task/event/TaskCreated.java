package tech.kayys.syirkah.project.domain.task.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskCreated(
        UUID eventId,
        Instant occurredAt,
        ProjectTaskId taskId,
        ProjectId projectId,
        String title
) implements DomainEvent {

    public TaskCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(taskId, "taskId cannot be null");
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
    }

    public static TaskCreated now(
            ProjectTaskId taskId,
            ProjectId projectId,
            String title
    ) {
        return new TaskCreated(
                UUID.randomUUID(),
                Instant.now(),
                taskId,
                projectId,
                title
        );
    }

    @Override
    public String eventType() {
        return "project.task-created";
    }
}
