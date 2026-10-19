package tech.kayys.numou.product.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.time.Instant;
import java.util.UUID;

public record ProductSpecificationChanged(
        UUID eventId,
        Instant occurredAt,
        ProductSpecificationId specificationId,
        ProductId productId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "product.product-specification-changed";
    }
}