package tech.kayys.syirkah.construction.domain.variation.event;

import tech.kayys.syirkah.construction.domain.variation.ChangeOrderType;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record ChangeOrderCreated(
        UUID eventId,
        Instant occurredAt,
        UUID changeOrderId,
        UUID projectId,
        String orderNumber,
        ChangeOrderType type
) implements DomainEvent {
    @Override public String eventType() { return "construction.change-order-created"; }
}
