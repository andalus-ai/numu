package tech.kayys.numou.project.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.DateRange;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record ProjectPlanned(
        UUID eventId,
        Instant occurredAt,
        ProjectId projectId,
        DateRange plannedPeriod
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.project-planned";
    }
}