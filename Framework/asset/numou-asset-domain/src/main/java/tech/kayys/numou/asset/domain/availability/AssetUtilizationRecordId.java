package tech.kayys.syirkah.asset.domain.availability;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Utilization record identity (ASSET-26 §15). */
public record AssetUtilizationRecordId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetUtilizationRecordId {
        Objects.requireNonNull(value, "AssetUtilizationRecordId value cannot be null");
    }

    public static AssetUtilizationRecordId of(UUID value) {
        return new AssetUtilizationRecordId(value);
    }

    public static AssetUtilizationRecordId generate() {
        return new AssetUtilizationRecordId(UUID.randomUUID());
    }

    public static AssetUtilizationRecordId fromString(String value) {
        return new AssetUtilizationRecordId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AssetUtilizationRecordId{" + value + "}";
    }
}
