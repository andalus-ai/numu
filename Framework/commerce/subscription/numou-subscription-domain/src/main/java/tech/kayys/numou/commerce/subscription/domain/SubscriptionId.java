package tech.kayys.syirkah.commerce.subscription.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record SubscriptionId(UUID value) implements DomainId<UUID> {

    public SubscriptionId {
        if (value == null) {
            throw new IllegalArgumentException("Subscription id cannot be null");
        }
    }

    public static SubscriptionId generate() {
        return new SubscriptionId(UUID.randomUUID());
    }

    public static SubscriptionId of(UUID value) {
        return new SubscriptionId(value);
    }
}
