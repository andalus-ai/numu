package tech.kayys.numou.asset.domain.availability;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Availability period identity (ASSET-26 §4). */
public record AssetAvailabilityPeriodId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetAvailabilityPeriodId {
        Objects.requireNonNull(value, "AssetAvailabilityPeriodId value cannot be null");
    }

    public static AssetAvailabilityPeriodId of(UUID value) {
        return new AssetAvailabilityPeriodId(value);
    }

    public static AssetAvailabilityPeriodId generate() {
        return new AssetAvailabilityPeriodId(UUID.randomUUID());
    }

    public static AssetAvailabilityPeriodId fromString(String value) {
        return new AssetAvailabilityPeriodId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AssetAvailabilityPeriodId{" + value + "}";
    }
}
