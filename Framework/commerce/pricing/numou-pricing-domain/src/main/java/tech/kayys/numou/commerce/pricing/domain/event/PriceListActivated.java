package tech.kayys.syirkah.commerce.pricing.domain.event;

import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceListActivated(
        UUID eventId,
        Instant occurredAt,
        PriceListId priceListId
) implements DomainEvent {

    public PriceListActivated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.pricing.price-list.activated";
    }
}
