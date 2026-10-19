package tech.kayys.numou.project.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ProjectCreated(
        UUID eventId,
        Instant occurredAt,
        ProjectId projectId,
        String projectNumber
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.project-created";
    }
}