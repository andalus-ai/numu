package tech.kayys.numou.workforce.domain.paycomponent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PayComponentId(UUID value) implements DomainId<UUID> {
    public PayComponentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PayComponentId of(UUID value) {
        return new PayComponentId(value);
    }

    public static PayComponentId of(String value) {
        return new PayComponentId(UUID.fromString(value));
    }

    public static PayComponentId generate() {
        return new PayComponentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
