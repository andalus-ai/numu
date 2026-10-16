package tech.kayys.syirkah.commerce.configuration.domain.event;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;

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
