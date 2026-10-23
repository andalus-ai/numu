package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Stable identity for a physical or operational cargo hub. */
public record HubId(UUID value) implements DomainId<UUID>, Serializable {
    public HubId {
        Objects.requireNonNull(value, "HubId value must not be null");
    }

    public static HubId of(UUID value) {
        return new HubId(value);
    }

    public static HubId of(String value) {
        return new HubId(UUID.fromString(value));
    }

    public static HubId generate() {
        return new HubId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
