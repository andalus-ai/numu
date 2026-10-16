package tech.kayys.syirkah.compliance.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Regulation identifier.
 */
public record RegulationId(UUID value) implements DomainId<UUID>, Serializable {

    public RegulationId {
        Objects.requireNonNull(value, "RegulationId value cannot be null");
    }

    public static RegulationId of(UUID value) {
        return new RegulationId(value);
    }

    public static RegulationId generate() {
        return new RegulationId(UUID.randomUUID());
    }

    public static RegulationId fromString(String value) {
        return new RegulationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "RegulationId{" + value + "}";
    }
}
