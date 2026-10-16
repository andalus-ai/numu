package tech.kayys.syirkah.commerce.offering.domain.event;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProductOfferingSuspended(
        UUID eventId,
        Instant occurredAt,
        ProductOfferingId offeringId,
        ProductId productId
) implements DomainEvent {

    public ProductOfferingSuspended {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(productId, "productId cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.offering.suspended";
    }
}
