package tech.kayys.syirkah.product.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

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