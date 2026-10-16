package tech.kayys.syirkah.commerce.offering.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record ProductOfferingId(UUID value) implements DomainId<UUID> {
    public ProductOfferingId {
        if (value == null) {
            throw new IllegalArgumentException("Product offering id cannot be null");
        }
    }

    public static ProductOfferingId generate() {
        return new ProductOfferingId(UUID.randomUUID());
    }

    public static ProductOfferingId of(UUID value) {
        return new ProductOfferingId(value);
    }
}
