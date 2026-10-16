package tech.kayys.syirkah.asset.domain.fixedasset;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an asset sub-component for componentized depreciation.
 */
public record ComponentId(String value) {
    public ComponentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("ComponentId must not be blank");
    }

    public static ComponentId generate() {
        return new ComponentId(UUID.randomUUID().toString());
    }
}
