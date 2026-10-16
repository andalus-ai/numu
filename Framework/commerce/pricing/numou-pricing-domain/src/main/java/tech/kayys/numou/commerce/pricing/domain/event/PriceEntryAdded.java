package tech.kayys.syirkah.commerce.pricing.domain.event;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceEntryId;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PriceEntryAdded(
        UUID eventId,
        Instant occurredAt,
        PriceListId priceListId,
        PriceEntryId entryId,
        ProductOfferingId offeringId,
        Money amount
) implements DomainEvent {

    public PriceEntryAdded {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(priceListId, "priceListId cannot be null");
        Objects.requireNonNull(entryId, "entryId cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.pricing.price-entry.added";
    }
}
