package tech.kayys.numou.project.domain.commercial.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.numou.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record AdvanceReceived(
        UUID eventId,
        Instant occurredAt,
        ProjectAdvanceId advanceId,
        ProjectId projectId,
        Money amount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.advance-received";
    }
}