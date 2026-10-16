package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductType;

import java.time.Instant;
import java.util.UUID;

public record ProductCreated(
        UUID eventId,
        Instant occurredAt,
        ProductId productId,
        String code,
        String name,
        ProductType productType
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-created";
    }
}