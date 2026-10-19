package tech.kayys.numou.project.domain.commercial.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record RetentionHeld(
        UUID eventId,
        Instant occurredAt,
        ProjectRetentionId retentionId,
        ProjectId projectId,
        Money amount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.retention-held";
    }
}