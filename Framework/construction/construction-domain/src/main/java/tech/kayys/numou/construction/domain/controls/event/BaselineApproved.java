package tech.kayys.syirkah.construction.domain.controls.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record BaselineApproved(
        UUID eventId,
        Instant occurredAt,
        UUID baselineId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.baseline-approved"; }
}
