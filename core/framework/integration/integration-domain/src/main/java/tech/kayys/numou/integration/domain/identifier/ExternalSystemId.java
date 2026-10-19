package tech.kayys.numou.integration.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Identifies a partner's system that Numou exchanges data with.
 */
public record ExternalSystemId(UUID value) implements DomainId<UUID>, Serializable {

    public ExternalSystemId {
        Objects.requireNonNull(value, "ExternalSystemId value cannot be null");
    }

    public static ExternalSystemId of(UUID value) {
        return new ExternalSystemId(value);
    }

    public static ExternalSystemId generate() {
        return new ExternalSystemId(UUID.randomUUID());
    }

    public static ExternalSystemId fromString(String value) {
        return new ExternalSystemId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "ExternalSystemId{" + value + "}";
    }
}
