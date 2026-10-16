package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.UUID;

public record ProductRenamed(
        UUID eventId,
        Instant occurredAt,
        ProductId productId,
        String oldName,
        String newName
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-renamed";
    }
}