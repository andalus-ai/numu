package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.sku.SkuId;

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