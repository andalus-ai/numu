package tech.kayys.numou.inventory.domain.location;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a functional facility.
 */
public record FacilityId(String value) {
    public FacilityId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("FacilityId cannot be blank");
        }
    }

    public static FacilityId generate() {
        return new FacilityId(UUID.randomUUID().toString());
    }

    public static FacilityId of(String value) {
        return new FacilityId(value);
    }
}
