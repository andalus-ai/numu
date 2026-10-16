package tech.kayys.numou.commerce.configuration.domain.event;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record ProductConfigurationCompleted(
        UUID eventId,
        Instant occurredAt,
        ProductConfigurationId configurationId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "commerce.product-configuration-completed";
    }
}
