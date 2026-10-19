package tech.kayys.numou.construction.domain.document.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record DrawingCreated(
        UUID eventId,
        Instant occurredAt,
        UUID drawingId,
        UUID projectId,
        String drawingNumber
) implements DomainEvent {
    @Override public String eventType() { return "construction.drawing-created"; }
}
