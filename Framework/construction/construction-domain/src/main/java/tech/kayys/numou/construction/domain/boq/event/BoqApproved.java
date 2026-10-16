package tech.kayys.syirkah.construction.domain.boq.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record BoqApproved(
        UUID eventId,
        Instant occurredAt,
        UUID boqId,
        UUID projectId,
        int currentRevision
) implements DomainEvent {
    @Override public String eventType() { return "construction.boq-approved"; }
}
