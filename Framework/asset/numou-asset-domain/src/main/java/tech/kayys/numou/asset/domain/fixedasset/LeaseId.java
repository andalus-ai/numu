package tech.kayys.syirkah.asset.domain.fixedasset;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an IFRS 16 lease contract.
 */
public record LeaseId(String value) {
    public LeaseId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("LeaseId must not be blank");
    }

    public static LeaseId generate() {
        return new LeaseId(UUID.randomUUID().toString());
    }
}
