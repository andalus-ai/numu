package tech.kayys.syirkah.analytics.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * KPI identifier.
 */
public record KPIId(UUID value) implements DomainId<UUID>, Serializable {

    public KPIId {
        Objects.requireNonNull(value, "KPIId value cannot be null");
    }

    public static KPIId of(UUID value) {
        return new KPIId(value);
    }

    public static KPIId generate() {
        return new KPIId(UUID.randomUUID());
    }

    public static KPIId fromString(String value) {
        return new KPIId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "KPIId{" + value + "}";
    }
}