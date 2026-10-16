package tech.kayys.syirkah.commerce.subscription.domain.event;

import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.subscription.domain.SubscriptionId;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SubscriptionStarted(
        UUID eventId,
        Instant occurredAt,
        SubscriptionId subscriptionId,
        ProductOfferingId offeringId
) implements DomainEvent {

    public SubscriptionStarted {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(offeringId, "offeringId cannot be null");
    }

    @Override
    public String eventType() {
        return "commerce.subscription.started";
    }
}
