package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import java.time.Instant;
import java.util.UUID;

public record ProductVariantActivated(
        UUID eventId,
        Instant occurredAt,
        ProductVariantId variantId,
        ProductId productId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-variant-activated";
    }
}