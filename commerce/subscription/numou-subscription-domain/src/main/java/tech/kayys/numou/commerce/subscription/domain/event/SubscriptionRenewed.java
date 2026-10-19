package tech.kayys.numou.commerce.subscription.domain.event;

import tech.kayys.numou.commerce.offering.domain.ProductOfferingId;
import tech.kayys.numou.commerce.subscription.domain.SubscriptionId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SubscriptionRenewed(
        UUID eventId,
        Instant occurredAt,
        SubscriptionId subscriptionId,
        ProductOfferingId offeringId
) implements DomainEvent {

    public SubscriptionRenewed {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.subscription.renewed";
    }
}
