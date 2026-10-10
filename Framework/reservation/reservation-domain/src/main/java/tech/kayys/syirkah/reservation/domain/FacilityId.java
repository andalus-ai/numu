package tech.kayys.syirkah.reservation.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for a physical or logical facility (branch, hotel, clinic, restaurant venue, studio, site).
 */
public record FacilityId(UUID value) implements DomainId<UUID> {
    public FacilityId {
        Objects.requireNonNull(value, "FacilityId value cannot be null");
    }

    public static FacilityId of(UUID value) {
        return new FacilityId(value);
    }

    public static FacilityId of(String value) {
        return new FacilityId(UUID.fromString(value));
    }

    public static FacilityId generate() {
        return new FacilityId(UUID.randomUUID());
    }
}
