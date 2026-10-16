package tech.kayys.syirkah.asset.domain.fixedasset;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a Construction-In-Progress (CIP) project.
 */
public record CipId(String value) {
    public CipId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("CipId must not be blank");
    }

    public static CipId generate() {
        return new CipId(UUID.randomUUID().toString());
    }
}
