package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record DiningTableId(UUID value) implements DomainId<UUID>, Serializable {

    public DiningTableId {
        Objects.requireNonNull(value, "DiningTableId value must not be null");
    }

    public static DiningTableId of(UUID value) {
        return new DiningTableId(value);
    }

    public static DiningTableId of(String value) {
        return new DiningTableId(UUID.fromString(value));
    }

    public static DiningTableId generate() {
        return new DiningTableId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
