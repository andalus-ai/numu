package tech.kayys.numou.fms.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record FuelTransactionId(UUID value) implements DomainId<UUID>, Serializable {

    public FuelTransactionId {
        Objects.requireNonNull(value, "FuelTransactionId value cannot be null");
    }

    public static FuelTransactionId of(UUID value) {
        return new FuelTransactionId(value);
    }

    public static FuelTransactionId generate() {
        return new FuelTransactionId(UUID.randomUUID());
    }

    public static FuelTransactionId fromString(String value) {
        return new FuelTransactionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "FuelTransactionId{" + value + "}";
    }
}
