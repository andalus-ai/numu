package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.sku.SkuId;
import tech.kayys.numou.product.domain.variant.ProductVariantId;

import java.time.Instant;
import java.util.UUID;

public record SkuCreated(
        UUID eventId,
        Instant occurredAt,
        SkuId skuId,
        ProductId productId,
        ProductVariantId variantId,
        String code,
        String name
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.sku-created";
    }
}