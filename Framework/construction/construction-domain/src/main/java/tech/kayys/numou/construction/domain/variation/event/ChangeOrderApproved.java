package tech.kayys.syirkah.construction.domain.variation.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ChangeOrderApproved(
        UUID eventId,
        Instant occurredAt,
        UUID changeOrderId,
        UUID projectId
) implements DomainEvent {
    @Override public String eventType() { return "construction.change-order-approved"; }
}
