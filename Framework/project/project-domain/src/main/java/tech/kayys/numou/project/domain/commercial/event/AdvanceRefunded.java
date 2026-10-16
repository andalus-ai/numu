package tech.kayys.syirkah.project.domain.commercial.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.UUID;

public record AdvanceRefunded(
        UUID eventId,
        Instant occurredAt,
        ProjectAdvanceId advanceId,
        ProjectId projectId,
        Money amount
) implements DomainEvent {

    @Override
    public String eventType() {
        return "project.advance-refunded";
    }
}