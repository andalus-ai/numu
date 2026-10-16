package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;

import java.time.Instant;
import java.util.UUID;

public record SkuIdentifierAdded(
        UUID eventId,
        Instant occurredAt,
        SkuId skuId,
        SkuIdentifierType identifierType,
        String value
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.sku-identifier-added";
    }
}