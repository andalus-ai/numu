package tech.kayys.syirkah.asset.domain.inspection;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link AssetInspection}. */
public record AssetInspectionId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetInspectionId {
        Objects.requireNonNull(value, "AssetInspectionId value cannot be null");
    }

    public static AssetInspectionId of(UUID value) {
        return new AssetInspectionId(value);
    }

    public static AssetInspectionId generate() {
        return new AssetInspectionId(UUID.randomUUID());
    }

    public static AssetInspectionId fromString(String value) {
        return new AssetInspectionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
