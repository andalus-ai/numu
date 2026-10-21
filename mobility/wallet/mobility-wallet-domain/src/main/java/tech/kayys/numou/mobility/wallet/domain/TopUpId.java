package tech.kayys.numou.mobility.wallet.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record TopUpId(UUID value) implements DomainId<UUID>, Serializable {

    public TopUpId {
        Objects.requireNonNull(value, "TopUpId value must not be null");
    }

    public static TopUpId of(UUID value) {
        return new TopUpId(value);
    }

    public static TopUpId generate() {
        return new TopUpId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
