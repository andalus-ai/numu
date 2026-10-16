package tech.kayys.syirkah.commerce.pricing.domain.event;

import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceListCreated(
        UUID eventId,
        Instant occurredAt,
        PriceListId priceListId,
        String code,
        String name
) implements DomainEvent {

    public PriceListCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.pricing.price-list.created";
    }
}
