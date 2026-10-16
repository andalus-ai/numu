package tech.kayys.numou.construction.domain.hse.event;

import tech.kayys.numou.construction.domain.hse.PermitType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record PermitToWorkIssued(
        UUID eventId,
        Instant occurredAt,
        UUID permitId,
        UUID siteId,
        PermitType type
) implements DomainEvent {
    @Override public String eventType() { return "construction.permit-to-work-issued"; }
}
