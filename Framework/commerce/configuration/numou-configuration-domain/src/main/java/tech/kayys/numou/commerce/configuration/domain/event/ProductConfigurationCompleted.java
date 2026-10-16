package tech.kayys.syirkah.commerce.configuration.domain.event;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

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
