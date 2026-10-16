package tech.kayys.syirkah.commerce.configuration.domain.event;

import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.specification.OptionGroupId;
import tech.kayys.syirkah.product.domain.specification.OptionId;

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
