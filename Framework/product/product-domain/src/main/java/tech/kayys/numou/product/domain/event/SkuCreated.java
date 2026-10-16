package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

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