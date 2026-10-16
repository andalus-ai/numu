package tech.kayys.syirkah.accounting.domain.close;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier of a Financial Close Cycle.
 */
public record CloseCycleId(String value) {
    public CloseCycleId {
        Objects.requireNonNull(value, "CloseCycleId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CloseCycleId value must not be blank");
        }
    }

    public static CloseCycleId generate() {
        return new CloseCycleId(UUID.randomUUID().toString());
    }

    public static CloseCycleId of(String value) {
        return new CloseCycleId(value);
    }
}
