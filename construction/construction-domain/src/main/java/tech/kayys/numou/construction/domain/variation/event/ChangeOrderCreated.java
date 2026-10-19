package tech.kayys.numou.construction.domain.variation.event;

import tech.kayys.numou.construction.domain.variation.ChangeOrderType;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
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
