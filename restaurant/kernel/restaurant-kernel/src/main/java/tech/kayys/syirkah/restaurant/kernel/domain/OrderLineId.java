package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record OrderLineId(UUID value) implements DomainId<UUID>, Serializable {

    public OrderLineId {
        Objects.requireNonNull(value, "OrderLineId value must not be null");
    }

    public static OrderLineId of(UUID value) {
        return new OrderLineId(value);
    }

    public static OrderLineId of(String value) {
        return new OrderLineId(UUID.fromString(value));
    }

    public static OrderLineId generate() {
        return new OrderLineId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
