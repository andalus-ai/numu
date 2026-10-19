package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.variant.ProductVariantId;

import java.time.Instant;
import java.util.UUID;

public record ProductVariantCreated(
        UUID eventId,
        Instant occurredAt,
        ProductVariantId variantId,
        ProductId productId,
        String code,
        String name
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-variant-created";
    }
}