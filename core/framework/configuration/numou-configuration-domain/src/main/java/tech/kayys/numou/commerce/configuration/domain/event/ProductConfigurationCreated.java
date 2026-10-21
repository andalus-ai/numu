package tech.kayys.numou.commerce.configuration.domain.event;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;

import java.time.Instant;
import java.util.UUID;

public record ProductConfigurationCreated(
        UUID eventId,
        Instant occurredAt,
        ProductConfigurationId configurationId,
        ProductId productId,
        ProductSpecificationId specificationId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "commerce.product-configuration-created";
    }
}
