package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record VenueId(UUID value) implements DomainId<UUID>, Serializable {

    public VenueId {
        Objects.requireNonNull(value, "VenueId value must not be null");
    }

    public static VenueId of(UUID value) {
        return new VenueId(value);
    }

    public static VenueId of(String value) {
        return new VenueId(UUID.fromString(value));
    }

    public static VenueId generate() {
        return new VenueId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
