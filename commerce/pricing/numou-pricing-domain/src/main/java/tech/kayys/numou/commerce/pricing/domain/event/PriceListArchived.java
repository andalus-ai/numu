package tech.kayys.numou.commerce.pricing.domain.event;

import tech.kayys.numou.commerce.pricing.domain.price.PriceListId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceListArchived(
        UUID eventId,
        Instant occurredAt,
        PriceListId priceListId
) implements DomainEvent {

    public PriceListArchived {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.pricing.price-list.archived";
    }
}
