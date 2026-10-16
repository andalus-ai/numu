package tech.kayys.syirkah.asset.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Asset identifier.
 */
public record AssetId(UUID value) implements DomainId<UUID>, Serializable {

    public AssetId {
        Objects.requireNonNull(value, "AssetId value cannot be null");
    }

    public static AssetId of(UUID value) {
        return new AssetId(value);
    }

    public static AssetId generate() {
        return new AssetId(UUID.randomUUID());
    }

    public static AssetId fromString(String value) {
        return new AssetId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "AssetId{" + value + "}";
    }
}