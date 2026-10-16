package tech.kayys.syirkah.project.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.project.ProjectId;

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