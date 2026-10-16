package tech.kayys.numou.commerce.pricing.domain.event;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.pricing.domain.price.PriceEntryId;
import tech.kayys.numou.commerce.pricing.domain.price.PriceListId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceEntryChanged(
        UUID eventId,
        Instant occurredAt,
        PriceListId priceListId,
        PriceEntryId entryId,
        ProductOfferingId offeringId,
        Money amount
) implements DomainEvent {

    public PriceEntryChanged {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.pricing.price-entry.changed";
    }
}
