package tech.kayys.numou.commerce.offering.domain.event;

import tech.kayys.numou.commerce.offering.domain.OfferingType;
import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.product.domain.product.ProductId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProductOfferingCreated(
        UUID eventId,
        Instant occurredAt,
        ProductOfferingId offeringId,
        ProductId productId,
        OfferingType type
) implements DomainEvent {

    public ProductOfferingCreated {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
        Objects.requireNonNull(productId, "productId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.offering.created";
    }
}
