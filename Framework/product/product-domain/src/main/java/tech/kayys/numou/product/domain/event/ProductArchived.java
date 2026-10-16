package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.Instant;
import java.util.UUID;

public record ProductArchived(
        UUID eventId,
        Instant occurredAt,
        ProductId productId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-archived";
    }
}