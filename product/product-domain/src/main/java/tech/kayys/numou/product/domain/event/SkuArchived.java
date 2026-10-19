package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.sku.SkuId;

import java.time.Instant;
import java.util.UUID;

public record SkuArchived(
        UUID eventId,
        Instant occurredAt,
        SkuId skuId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.sku-archived";
    }
}