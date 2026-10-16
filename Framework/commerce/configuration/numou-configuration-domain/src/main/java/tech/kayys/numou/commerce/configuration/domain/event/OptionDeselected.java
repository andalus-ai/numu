package tech.kayys.numou.commerce.configuration.domain.event;

import tech.kayys.numou.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.specification.OptionGroupId;
import tech.kayys.numou.product.domain.specification.OptionId;

import java.time.Instant;
import java.util.UUID;

public record OptionDeselected(
        UUID eventId,
        Instant occurredAt,
        ProductConfigurationId configurationId,
        OptionGroupId optionGroupId,
        OptionId optionId
) implements DomainEvent {

    @Override
    public String eventType() {
        return "commerce.option-deselected";
    }
}
